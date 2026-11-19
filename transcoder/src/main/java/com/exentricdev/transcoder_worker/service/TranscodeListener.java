package com.exentricdev.transcoder_worker.service;

import com.exentricdev.transcoder_worker.config.RabbitConfig;
import com.exentricdev.transcoder_worker.dto.ProcessedVideo;
import com.exentricdev.transcoder_worker.dto.TranscodeJob;
import com.exentricdev.transcoder_worker.dto.TranscodeProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Consumes transcode jobs.
 *
 * Reliability rules:
 * <ul>
 *   <li><b>Atomic output:</b> ffmpeg writes into a private temp directory that is renamed to the final
 *       directory only after everything succeeded, so a crash can never leave a half-written video that
 *       looks finished.</li>
 *   <li><b>Idempotent:</b> if the final directory already exists (a redelivered job that finished before the
 *       ack was lost), the success message is re-published instead of silently dropping the job.</li>
 *   <li><b>One worker per video:</b> a lock file guards against two workers transcoding the same video. The lock
 *       is touched on every progress update, so it only counts as stale after two minutes of silence.</li>
 *   <li><b>Bounded retries:</b> exceptions are retried with backoff (see application.properties), then the job
 *       is dead-lettered to {@code transcoder-failures}, where the backend marks the video FAILED.</li>
 * </ul>
 */
@Service
public class TranscodeListener {
    private static final Logger log = LoggerFactory.getLogger(TranscodeListener.class);
    private static final long STALE_LOCK_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long PROGRESS_INTERVAL_MS = 1_000;

    private final RabbitTemplate rabbitTemplate;
    private final HlsTranscoder transcoder;

    public TranscodeListener(RabbitTemplate rabbitTemplate, HlsTranscoder transcoder) {
        this.rabbitTemplate = rabbitTemplate;
        this.transcoder = transcoder;
    }

    @RabbitListener(queues = RabbitConfig.JOBS)
    public void receiveMessage(TranscodeJob job) throws IOException, InterruptedException {
        MDC.put("videoId", job.videoId());
        try {
            process(job);
        } finally {
            MDC.remove("videoId");
        }
    }

    private void process(TranscodeJob job) throws IOException, InterruptedException {
        Path input = Path.of(job.inputPath());
        Path outDir = Path.of(job.outputDir());
        log.info("Received job: videoId={}, input={}, outputDir={}", job.videoId(), input, outDir);

        if (Files.isRegularFile(outDir.resolve("master.m3u8"))) {
            log.info("Output already published for videoId={}, re-sending completion", job.videoId());
            publishDone(job, outDir, readDuration(outDir), renditionsIn(outDir));
            return;
        }
        if (!Files.isRegularFile(input)) {
            throw new AmqpRejectAndDontRequeueException("Input file is missing: " + input);
        }

        Path lock = Path.of(job.outputDir() + ".lock");
        acquireLock(lock, job.videoId());
        Path tmp = outDir.resolveSibling(outDir.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            long[] lastSent = {0};
            int[] lastPercent = {-1};
            HlsTranscoder.Result result = transcoder.transcode(input, tmp, percent -> {
                long now = System.currentTimeMillis();
                if (percent == lastPercent[0] || now - lastSent[0] < PROGRESS_INTERVAL_MS) return;
                lastSent[0] = now;
                lastPercent[0] = percent;
                touch(lock);
                sendProgress(job.videoId(), percent);
            });

            Files.writeString(tmp.resolve("duration.txt"), String.valueOf(result.probe().durationSeconds()));
            publishAtomically(tmp, outDir);
            publishDone(job, outDir, result.probe().durationSeconds(), result.renditions());
            log.info("Transcoded videoId={} into {}p", job.videoId(), result.renditions());
        } catch (HlsTranscoder.UnprocessableInputException e) {
            log.error("Unprocessable input for videoId={}: {}", job.videoId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } finally {
            deleteRecursively(tmp);
            Files.deleteIfExists(lock);
        }
    }

    private void acquireLock(Path lock, String videoId) throws IOException {
        try {
            Files.createFile(lock);
        } catch (FileAlreadyExistsException e) {
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
            if (age < STALE_LOCK_MS) {
                // another worker is actively transcoding this video; let this delivery go through retry/backoff
                throw new IllegalStateException("videoId=" + videoId + " is being transcoded by another worker");
            }
            log.warn("Removing stale lock for videoId={} (untouched for {}s)", videoId, age / 1000);
            Files.deleteIfExists(lock);
            Files.createFile(lock);
        }
    }

    /** Rename the finished temp dir into place. Same volume, so this is a single atomic rename. */
    private static void publishAtomically(Path tmp, Path outDir) throws IOException {
        try {
            Files.move(tmp, outDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, outDir);
        }
    }

    private void publishDone(TranscodeJob job, Path outDir, double duration, List<Integer> renditions) {
        Path poster = outDir.resolve("poster.jpg");
        rabbitTemplate.convertAndSend(RabbitConfig.PROCESSED, new ProcessedVideo(
                job.videoId(),
                outDir.resolve("master.m3u8").toString(),
                Files.isRegularFile(poster) ? poster.toString() : null,
                duration,
                renditions));
    }

    private void sendProgress(String videoId, int percent) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PROGRESS, new TranscodeProgress(videoId, percent));
        } catch (AmqpException e) {
            // progress is best-effort; never fail a transcode because an update couldn't be sent
            log.debug("Could not send progress for videoId={}: {}", videoId, e.getMessage());
        }
    }

    private static void touch(Path lock) {
        try {
            Files.setLastModifiedTime(lock, FileTime.from(Instant.now()));
        } catch (IOException ignored) {
            // worst case another worker treats the lock as stale after two minutes
        }
    }

    private static double readDuration(Path outDir) {
        try {
            return Double.parseDouble(Files.readString(outDir.resolve("duration.txt")).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    /** Heights of the renditions in a finished directory, read back from the master playlist. */
    private static List<Integer> renditionsIn(Path outDir) {
        List<Integer> heights = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(outDir.resolve("master.m3u8"))) {
                int i = line.indexOf("RESOLUTION=");
                if (i < 0) continue;
                String[] wh = line.substring(i + "RESOLUTION=".length()).split(",")[0].split("x");
                if (wh.length == 2 && wh[1].matches("\\d+")) heights.add(Integer.parseInt(wh[1]));
            }
        } catch (IOException ignored) {
            // leave it empty; the backend treats renditions as informational
        }
        return heights;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up {}: {}", dir, e.getMessage());
        }
    }
}
