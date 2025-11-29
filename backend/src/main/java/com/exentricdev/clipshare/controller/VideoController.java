package com.exentricdev.clipshare.controller;

import com.exentricdev.clipshare.DTO.VideoPatch;
import com.exentricdev.clipshare.DTO.VideoResponse;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.service.EmitterService;
import com.exentricdev.clipshare.service.VideoService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {
    public VideoService videoService;
    public EmitterService emitterService;

    public VideoController (VideoService videoService, EmitterService emitterService) {
        this.videoService = videoService;
        this.emitterService = emitterService;
    }

    @PostMapping()
    public VideoResponse handleFileUpload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String userId = jwt.getSubject();

        Video video = videoService.uploadVideo(file, userId);
        return new VideoResponse(video.getId(), video.getTitle(), video.getSize(), video.isProcessed());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVideo(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        String userId = jwt.getSubject();
        boolean deleted = videoService.deleteVideo(id, userId);

        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VideoResponse> updateVideo(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody VideoPatch updates) {
        String userId = jwt.getSubject();
        Video updatedVideo = videoService.updateVideo(id, userId, updates);

        VideoResponse response = new VideoResponse(updatedVideo.getId(), updatedVideo.getTitle(), updatedVideo.getSize(), updatedVideo.isProcessed());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getVideoById(@PathVariable String id) {
        Video video = videoService.getVideoById(id);

        if (!video.isProcessed()) {
            return ResponseEntity.status(202).build();
        }

        Path path = Path.of(video.getUrl());
        long fileSize = video.getSize();
        String contentType = video.getContentType();

        Resource videoResource = new FileSystemResource(path.toFile());

        return ResponseEntity.ok()
                .contentLength(fileSize)
                .header("Content-Type", contentType)
                .body(videoResource);
    }

    @GetMapping()
    public List<VideoResponse> getAllVideos() {
        List<Video> videos = videoService.getAllVideos();
        return videos.stream()
                .map(video -> new VideoResponse(video.getId(), video.getTitle(), video.getSize(), video.isProcessed()))
                .toList();
    }

    @GetMapping("/my-videos")
    public List<VideoResponse> getMyVideos(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<Video> videos = videoService.getVideosByUploader(userId);

        return videos.stream()
                .map(video -> new VideoResponse(video.getId(), video.getTitle(), video.getSize(), video.isProcessed()))
                .toList();
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String userId = jwt.getSubject();
        return emitterService.createEmitterForUser(userId);
    }
}
