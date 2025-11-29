package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.ProcessedVideo;
import com.exentricdev.clipshare.DTO.VideoResponse;
import com.exentricdev.clipshare.repository.VideoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProcessedListener {
    private static final Logger log = LoggerFactory.getLogger(ProcessedListener.class);

    private final VideoRepository videoRepository;
    private final EmitterService emitterService;

    public ProcessedListener(VideoRepository videoRepository, EmitterService emitterService) {
        this.videoRepository = videoRepository;
        this.emitterService = emitterService;
    }

    @RabbitListener(queues = "processed-videos")
    public void handleProcessedVideo(ProcessedVideo processedVideo) {
        String videoId = processedVideo.videoId();
        String outputPath = processedVideo.outputPath();

        log.info("Received processed video notification: videoId={}, outputPath={}", videoId, outputPath);

        videoRepository.findById(videoId)
                .map(video -> {
                    String rawUrl = video.getUrl();

                    video.setUrl(outputPath);
                    video.setProcessed(true);

                    // Delete the original unprocessed file
                    try {
                        Files.deleteIfExists(Path.of(rawUrl));
                    } catch (IOException e) {
                        log.error("Failed to delete original file for videoId: {}", videoId, e);
                    }

                    // Send notification to user about processing completion
                    VideoResponse payload = new VideoResponse(video.getId(), video.getTitle(), video.getSize(), video.isProcessed());

                    emitterService.notifyUser(
                            video.getUploaderId(),
                            "video-processed",
                            payload
                    );

                    return videoRepository.save(video);
                })
                .ifPresentOrElse(
                        video -> log.info("Updated video as processed for videoId: {}", videoId),
                        () -> log.error("Video not found for videoId: {}", videoId)
                );
    }
}
