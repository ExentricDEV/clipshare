package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.TranscodeJob;
import com.exentricdev.clipshare.config.RabbitConfig;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Drains the dead-letter queue. A job lands here after the worker exhausted its retries (or rejected an input
 * it can never process), and the video is marked FAILED so the user sees what happened and can upload again.
 */
@Service
public class FailureListener {
    private static final Logger log = LoggerFactory.getLogger(FailureListener.class);

    private final VideoRepository videoRepository;
    private final EmitterService emitterService;
    private final VideoMapper mapper;

    public FailureListener(VideoRepository videoRepository, EmitterService emitterService, VideoMapper mapper) {
        this.videoRepository = videoRepository;
        this.emitterService = emitterService;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitConfig.FAILURES)
    @Transactional
    public void handleFailedJob(TranscodeJob job, @Header(name = "x-death", required = false) List<Map<String, ?>> deaths) {
        Video video = videoRepository.findById(job.videoId()).orElse(null);
        if (video == null || video.getStatus() == VideoStatus.READY) {
            // deleted meanwhile, or a stale duplicate of a job that later succeeded
            return;
        }
        String reason = deaths != null && !deaths.isEmpty() && deaths.getFirst().get("reason") != null
                ? "Transcoding failed after retries (" + deaths.getFirst().get("reason") + ")"
                : "Transcoding failed after retries";
        video.setStatus(VideoStatus.FAILED);
        video.setFailureReason(reason);
        videoRepository.save(video);

        log.error("videoId={} FAILED: {}", video.getId(), reason);
        emitterService.notifyUser(video.getUploaderId(), "video-failed", mapper.toResponse(video));
    }
}
