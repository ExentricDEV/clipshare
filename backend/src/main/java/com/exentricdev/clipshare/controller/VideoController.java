package com.exentricdev.clipshare.controller;

import com.exentricdev.clipshare.DTO.PlaybackResponse;
import com.exentricdev.clipshare.DTO.VideoPatch;
import com.exentricdev.clipshare.DTO.VideoResponse;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.security.Roles;
import com.exentricdev.clipshare.service.EmitterService;
import com.exentricdev.clipshare.service.VideoMapper;
import com.exentricdev.clipshare.service.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Video metadata and lifecycle. Media bytes are served by {@link MediaController}. Access rules: see SecurityConfig. */
@RestController
@RequestMapping("/api/videos")
public class VideoController {
    private final VideoService videoService;
    private final EmitterService emitterService;
    private final VideoMapper mapper;

    public VideoController(VideoService videoService, EmitterService emitterService, VideoMapper mapper) {
        this.videoService = videoService;
        this.emitterService = emitterService;
        this.mapper = mapper;
    }

    @PostMapping
    public VideoResponse upload(Authentication auth, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        return mapper.toResponse(videoService.uploadVideo(file, Roles.userId(auth)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String id) {
        videoService.deleteVideo(id, Roles.userId(auth), Roles.isAdmin(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public VideoResponse update(Authentication auth, @PathVariable String id, @RequestBody VideoPatch updates) {
        return mapper.toResponse(videoService.updateVideo(id, Roles.userId(auth), Roles.isAdmin(auth), updates));
    }

    @GetMapping("/my-videos")
    public List<VideoResponse> myVideos(Authentication auth) {
        return videoService.getVideosByUploader(Roles.userId(auth)).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/public")
    public List<VideoResponse> publicVideos() {
        return videoService.getPublicVideos().stream().map(mapper::toResponse).toList();
    }

    /** Moderation view: every video from every user (ROLE_ADMIN only). */
    @GetMapping("/all")
    public List<VideoResponse> allVideos() {
        return videoService.getAllVideos().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public VideoResponse get(Authentication auth, @PathVariable String id) {
        return mapper.toResponse(videoService.getWatchableVideo(id, Roles.userId(auth), Roles.isAdmin(auth)));
    }

    /**
     * Checks the caller may watch the video, then returns short-lived signed URLs for the player.
     * 409 while it is still transcoding or if transcoding failed.
     */
    @GetMapping("/{id}/playback")
    public PlaybackResponse playback(Authentication auth, @PathVariable String id) {
        Video video = videoService.getWatchableVideo(id, Roles.userId(auth), Roles.isAdmin(auth));
        if (video.getStatus() != VideoStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video is " + video.getStatus().name().toLowerCase());
        }
        return mapper.toPlayback(video);
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication auth) {
        return emitterService.createEmitterForUser(Roles.userId(auth));
    }
}
