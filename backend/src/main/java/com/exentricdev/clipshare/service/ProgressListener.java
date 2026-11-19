package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.TranscodeProgress;
import com.exentricdev.clipshare.DTO.VideoProgressEvent;
import com.exentricdev.clipshare.config.RabbitConfig;
import com.exentricdev.clipshare.repository.VideoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/** Relays the worker's transcode progress to the uploader's open browser tabs. */
@Service
public class ProgressListener {
    private final VideoRepository videoRepository;
    private final EmitterService emitterService;

    public ProgressListener(VideoRepository videoRepository, EmitterService emitterService) {
        this.videoRepository = videoRepository;
        this.emitterService = emitterService;
    }

    @RabbitListener(queues = RabbitConfig.PROGRESS)
    public void handleProgress(TranscodeProgress update) {
        int percent = Math.max(0, Math.min(99, update.percent()));
        // the update only applies if it moves an active job forward (see recordProgress), so late or
        // out-of-order messages are dropped instead of making a finished video look unfinished
        if (videoRepository.recordProgress(update.videoId(), percent) == 0) return;
        videoRepository.findById(update.videoId()).ifPresent(video ->
                emitterService.notifyUser(video.getUploaderId(), "video-progress", new VideoProgressEvent(video.getId(), percent)));
    }
}
