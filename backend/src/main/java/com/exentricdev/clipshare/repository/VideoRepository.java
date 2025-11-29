package com.exentricdev.clipshare.repository;

import com.exentricdev.clipshare.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, String> {
    List<Video> findByUploaderId(String uploaderId);
    long countByUploaderIdAndIsProcessed(String uploaderId, Boolean isProcessed);
}
