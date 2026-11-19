package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.ProcessedVideo;
import com.exentricdev.clipshare.config.RabbitConfig;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/**
 * Marks a video READY when the worker reports its HLS ladder is published.
 * Idempotent: a duplicate message just rewrites the same fields.
 */
@Service
public class ProcessedListener {
    private static final Logger log = LoggerFactory.getLogger(ProcessedListener.class);

    private final VideoRepository videoRepository;
    private final EmitterService emitterService;
    private final VideoMapper mapper;

    public ProcessedListener(VideoRepository videoRepository, EmitterService emitterService, VideoMapper mapper) {
        this.videoRepository = videoRepository;
        this.emitterService = emitterService;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitConfig.PROCESSED)
    @Transactional
    public void handleProcessedVideo(ProcessedVideo done) {
        Video video = videoRepository.findById(done.videoId()).orElse(null);
        if (video == null) {
            // deleted while it was transcoding; nothing to update
            log.warn("Processed video {} no longer exists", done.videoId());
            return;
        }
        String rawPath = video.getStatus() == VideoStatus.READY ? null : video.getUrl();

        video.setUrl(done.masterPlaylistPath());
        video.setPosterPath(done.posterPath());
        video.setDurationSeconds(done.durationSeconds());
        video.setRenditions(done.renditions());
        video.setProgress(100);
        video.setFailureReason(null);
        video.setStatus(VideoStatus.READY);
        videoRepository.save(video);

        if (rawPath != null && !rawPath.equals(done.masterPlaylistPath())) VideoService.deleteQuietly(Path.of(rawPath));
        emitterService.notifyUser(video.getUploaderId(), "video-processed", mapper.toResponse(video));
        log.info("videoId={} is READY ({}p)", video.getId(), done.renditions());
    }
}
