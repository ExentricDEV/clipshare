package com.exentricdev.clipshare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
public class Video {
    @Id
    private String id;

    @Column(nullable = false)
    private String uploaderId;

    @Column(nullable = false)
    private String title;

    private String url;

    @Column(nullable = false)
    private Boolean isProcessed = false;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status = VideoStatus.PENDING;

    @Column(nullable = false)
    private int progress = 0;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PRIVATE;

    private String posterPath;

    private Double durationSeconds;

    /** comma-separated rendition heights, e.g. "1080,720,480" */
    private String renditions;

    protected Video() {
    }

    public Video(String uploaderId, String title, String url, Boolean isProcessed, String storedFileName, Long size, String contentType, Instant uploadedAt) {
        this.uploaderId = uploaderId;
        this.title = title;
        this.url = url;
        this.isProcessed = isProcessed;
        this.storedFileName = storedFileName;
        this.size = size;
        this.contentType = contentType;
        this.uploadedAt = uploadedAt;
    }

    public String getId() {
        return id;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Boolean isProcessed() {
        return isProcessed;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public Long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setProcessed(Boolean isProcessed) {
        this.isProcessed = isProcessed;
    }

    public void setStoredFileName(String name) {
        this.storedFileName = name;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
        this.isProcessed = status == VideoStatus.READY;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason == null ? null : failureReason.substring(0, Math.min(failureReason.length(), 500));
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<Integer> getRenditions() {
        if (renditions == null || renditions.isBlank()) return List.of();
        return Arrays.stream(renditions.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }

    public void setRenditions(List<Integer> heights) {
        this.renditions = heights == null ? null : heights.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** true when the output is an HLS ladder (a master playlist), false for legacy single-MP4 videos */
    public boolean isHls() {
        return url != null && url.endsWith(".m3u8");
    }

    @Override
    public String toString() {
        return "Video{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", storedFileName='" + storedFileName + '\'' +
                ", size=" + size +
                ", contentType='" + contentType + '\'' +
                ", uploadTimestamp='" + uploadedAt + '\'' +
                '}';
        }
}
