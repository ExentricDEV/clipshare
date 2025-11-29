package com.exentricdev.transcoder_worker.service;

import com.exentricdev.transcoder_worker.dto.ProcessedVideo;
import com.exentricdev.transcoder_worker.dto.TranscodeJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TranscodeListener {
    private static final Logger log = LoggerFactory.getLogger(TranscodeListener.class);
    private final RabbitTemplate rabbitTemplate;

    public TranscodeListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "video-transcoder", containerFactory = "rabbitListenerContainerFactory")
    public void receiveMessage(TranscodeJob message) {
        log.info("Received message: videoId={}, inputPath={}, outputPath={}", message.videoId(), message.inputPath(), message.outputPath());

        String inputPath = message.inputPath();
        String outputPath = message.outputPath();

        Path processedFile = Path.of(outputPath);
        Path lockFile = Path.of(outputPath + ".lock");

        if (Files.exists(processedFile)) {
            log.info("Output file already exists for videoId: {}", message.videoId());
            return;
        }

        try {
            // Lock file handling
            try {
                Files.createFile(lockFile);
            } catch (FileAlreadyExistsException e) {
                long lockAge = System.currentTimeMillis() - Files.getLastModifiedTime(lockFile).toMillis();

                if (lockAge > TimeUnit.SECONDS.toMillis(120)) { // Slightly longer than process timeout
                    log.warn("Stale lock detected for videoId: {}, removing and proceeding", message.videoId());
                    Files.delete(lockFile);
                    Files.createFile(lockFile);
                } else {
                    log.error("Lock file exists for videoId: {}, another worker is processing", message.videoId());
                    throw new AmqpRejectAndDontRequeueException("Lock file exists for videoId: " + message.videoId());
                }
            }

            // Start transcoding process
            Process process = buildAndStartProcess(inputPath, outputPath);

            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished && process.isAlive()) {
                boolean died = process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);

                if (!died) {
                    log.error("Failed to terminate transcoding process for videoId: {}", message.videoId());
                    throw new AmqpRejectAndDontRequeueException("Failed to terminate transcoding process for videoId: " + message.videoId());
                }

                throw new TimeoutException("Transcoding process timed out for videoId: " + message.videoId());
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                // Send success message to processed-videos queue
                rabbitTemplate.convertAndSend("processed-videos", new ProcessedVideo(
                        message.videoId(),
                        outputPath
                ));
                log.info("Successfully transcoded videoId: {}", message.videoId());
            } else {
                throw new RuntimeException("Transcoding failed for videoId: " + message.videoId() + " with exit code: " + exitCode);
            }
        } catch (IllegalStateException | FileNotFoundException e) {
            // Non-retryable errors
            log.error("Non-retryable error for videoId: {} - {}", message.videoId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Non-retryable error: " + e.getMessage(), e);
        } catch (IOException | InterruptedException | TimeoutException e) {
            // Retryable errors
            log.error("Error processing videoId: {} - {}", message.videoId(), e.getMessage(), e);
            throw new RuntimeException("Retryable error for videoId: " + message.videoId(), e);
        } catch (Exception e) {
            // Unexpected errors, treat as retryable
            log.error("Unexpected error processing videoId: {} - {}", message.videoId(), e.getMessage(), e);
            throw new RuntimeException("Unexpected error for videoId: " + message.videoId(), e);
        } finally {
            // Cleanup lock file
            try {
                Files.deleteIfExists(lockFile);
            } catch (IOException e) {
                log.warn("Failed to delete lock file for videoId: {}", message.videoId(), e);
            }
        }
    }

    private Process buildAndStartProcess(String inputPath, String outputPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-i", inputPath,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "22",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                outputPath
        );

        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }
}
