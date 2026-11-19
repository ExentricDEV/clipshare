package com.exentricdev.transcoder_worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Turns one input file into an adaptive-bitrate HLS ladder with a single ffmpeg run:
 * the source is decoded once, split, and scaled into each rendition, so CPU cost is
 * one decode plus N encodes.
 *
 * Output layout (inside {@code outDir}):
 * <pre>
 *   master.m3u8          lists every rendition with its bandwidth and resolution
 *   v0/index.m3u8        highest rendition's playlist
 *   v0/seg_000.ts ...    4-second segments
 *   v1/...               next rendition down
 *   poster.jpg           thumbnail
 *   ffmpeg.log           ffmpeg's stderr, kept for debugging failed jobs
 * </pre>
 */
@Component
public class HlsTranscoder {
    private static final Logger log = LoggerFactory.getLogger(HlsTranscoder.class);

    /** Height, video bitrate (kbps). Rungs taller than the source are skipped so we never upscale. */
    static final int[][] LADDER = {
            {1080, 5000},
            {720, 2800},
            {480, 1400},
            {360, 800},
    };
    static final int SEGMENT_SECONDS = 4;

    private final Duration minTimeout;
    private final double timeoutPerMediaSecond;

    public HlsTranscoder(@Value("${app.transcode.min-timeout:120s}") Duration minTimeout,
                         @Value("${app.transcode.timeout-per-media-second:4}") double timeoutPerMediaSecond) {
        this.minTimeout = minTimeout;
        this.timeoutPerMediaSecond = timeoutPerMediaSecond;
    }

    public record Probe(double durationSeconds, int height, boolean hasAudio) {
    }

    public record Result(Probe probe, List<Integer> renditions) {
    }

    /** Thrown for inputs that will never succeed (not a video, corrupt header): retrying is pointless. */
    public static class UnprocessableInputException extends RuntimeException {
        public UnprocessableInputException(String message) {
            super(message);
        }
    }

    public Result transcode(Path input, Path outDir, IntConsumer onProgress) throws IOException, InterruptedException {
        Probe probe = probe(input);
        List<int[]> rungs = rungsFor(probe.height());
        Files.createDirectories(outDir);
        for (int i = 0; i < rungs.size(); i++) Files.createDirectories(outDir.resolve("v" + i));

        List<String> cmd = buildCommand(input, outDir, rungs, probe.hasAudio());
        Path ffmpegLog = outDir.resolve("ffmpeg.log");
        ProcessBuilder pb = new ProcessBuilder(cmd)
                // stderr goes to a file, and stdout (progress) is read continuously below, so ffmpeg can never
                // block on a full pipe buffer
                .redirectError(ProcessBuilder.Redirect.to(ffmpegLog.toFile()));

        Duration timeout = timeoutFor(probe.durationSeconds());
        log.info("Transcoding {} ({}s, {}p, audio={}) into {} renditions, timeout {}",
                input.getFileName(), Math.round(probe.durationSeconds()), probe.height(), probe.hasAudio(), rungs.size(), timeout);

        Process process = pb.start();
        Thread progressReader = Thread.ofVirtual().start(() -> readProgress(process, probe.durationSeconds(), onProgress));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                throw new IOException("ffmpeg timed out after " + timeout);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            progressReader.join(2_000);
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg exited with code " + process.exitValue() + ": " + tail(ffmpegLog, 3));
        }

        extractPoster(input, outDir.resolve("poster.jpg"), probe.durationSeconds());
        List<Integer> heights = rungs.stream().map(r -> r[0]).toList();
        return new Result(probe, heights);
    }

    /** ffprobe the input for duration, video height, and whether it has an audio stream. */
    Probe probe(Path input) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("ffprobe", "-v", "error",
                "-show_entries", "format=duration:stream=codec_type,height",
                "-of", "default=noprint_wrappers=1", input.toString())
                .redirectErrorStream(true)
                .start();
        String out;
        try (var r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            out = String.join("\n", r.lines().toList());
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffprobe timed out");
        }
        if (p.exitValue() != 0) throw new UnprocessableInputException("ffprobe could not read the file: " + firstLine(out));

        double duration = 0;
        int height = 0;
        boolean hasVideo = false, hasAudio = false;
        for (String line : out.split("\n")) {
            String[] kv = line.trim().split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "codec_type" -> {
                    if (kv[1].equals("video")) hasVideo = true;
                    if (kv[1].equals("audio")) hasAudio = true;
                }
                case "height" -> {
                    if (height == 0 && kv[1].matches("\\d+")) height = Integer.parseInt(kv[1]);
                }
                case "duration" -> {
                    if (kv[1].matches("[\\d.]+")) duration = Double.parseDouble(kv[1]);
                }
                default -> {
                }
            }
        }
        if (!hasVideo || height == 0) throw new UnprocessableInputException("no video stream found");
        return new Probe(duration, height, hasAudio);
    }

    /** Every ladder rung at or below the source height; a source shorter than the lowest rung gets one rendition at its own height. */
    static List<int[]> rungsFor(int sourceHeight) {
        List<int[]> rungs = new ArrayList<>();
        for (int[] rung : LADDER) if (rung[0] <= sourceHeight) rungs.add(rung);
        if (rungs.isEmpty()) {
            int even = sourceHeight - (sourceHeight % 2);
            rungs.add(new int[]{Math.max(even, 2), LADDER[LADDER.length - 1][1]});
        }
        return rungs;
    }

    static List<String> buildCommand(Path input, Path outDir, List<int[]> rungs, boolean hasAudio) {
        int n = rungs.size();
        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-nostats", "-y", "-i", input.toString()));

        // decode once, split into n copies, scale each (width -2 keeps aspect ratio with an even width)
        StringBuilder filter = new StringBuilder("[0:v]split=" + n);
        for (int i = 0; i < n; i++) filter.append("[s").append(i).append(']');
        for (int i = 0; i < n; i++) {
            filter.append(";[s").append(i).append("]scale=w=-2:h=").append(rungs.get(i)[0]).append("[v").append(i).append(']');
        }
        cmd.addAll(List.of("-filter_complex", filter.toString()));

        StringBuilder streamMap = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int kbps = rungs.get(i)[1];
            cmd.addAll(List.of("-map", "[v" + i + "]",
                    "-c:v:" + i, "libx264",
                    "-b:v:" + i, kbps + "k",
                    "-maxrate:v:" + i, Math.round(kbps * 1.07) + "k",
                    "-bufsize:v:" + i, (kbps * 3 / 2) + "k"));
            if (hasAudio) cmd.addAll(List.of("-map", "a:0"));
            if (i > 0) streamMap.append(' ');
            streamMap.append("v:").append(i);
            if (hasAudio) streamMap.append(",a:").append(i);
        }
        cmd.addAll(List.of(
                "-preset", "fast",
                "-pix_fmt", "yuv420p",
                // keyframe exactly every segment boundary so every rendition can switch at the same points
                "-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT_SECONDS + ")",
                "-sc_threshold", "0"));
        if (hasAudio) cmd.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2"));
        cmd.addAll(List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(SEGMENT_SECONDS),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", outDir.resolve("v%v").resolve("seg_%03d.ts").toString(),
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", streamMap.toString(),
                "-progress", "pipe:1",
                outDir.resolve("v%v").resolve("index.m3u8").toString()));
        return cmd;
    }

    /** Parses ffmpeg's -progress key=value stream and reports whole percentages. */
    private static void readProgress(Process process, double durationSeconds, IntConsumer onProgress) {
        try (var r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                // out_time_us is microseconds of output written so far (out_time_ms is too, despite its name)
                if (line.startsWith("out_time_us=") && durationSeconds > 0) {
                    String v = line.substring("out_time_us=".length()).trim();
                    if (!v.matches("\\d+")) continue;
                    double seconds = Long.parseLong(v) / 1_000_000.0;
                    onProgress.accept((int) Math.min(99, Math.floor(seconds / durationSeconds * 100)));
                }
            }
        } catch (IOException e) {
            // the stream closes when ffmpeg exits or is killed; nothing to do
        }
    }

    private static void extractPoster(Path input, Path poster, double durationSeconds) {
        double at = durationSeconds > 2 ? 1.0 : 0.0;
        try {
            Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", String.valueOf(at), "-i", input.toString(),
                    "-frames:v", "1", "-vf", "scale=-2:360", poster.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (IOException | InterruptedException e) {
            // a missing thumbnail shouldn't fail the whole job
            log.warn("Could not extract poster for {}: {}", input.getFileName(), e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    Duration timeoutFor(double durationSeconds) {
        Duration scaled = Duration.ofMillis((long) (durationSeconds * timeoutPerMediaSecond * 1000));
        return scaled.compareTo(minTimeout) > 0 ? scaled : minTimeout;
    }

    private static String tail(Path file, int lines) {
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            return String.join(" | ", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(no ffmpeg log)";
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).trim();
    }
}
