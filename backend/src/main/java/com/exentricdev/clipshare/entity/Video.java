package com.exentricdev.clipshare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

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
