package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.TranscodeJob;
import com.exentricdev.clipshare.DTO.VideoPatch;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.exception.BadRequestException;
import com.exentricdev.clipshare.exception.ConflictException;
import com.exentricdev.clipshare.exception.ForbiddenException;
import com.exentricdev.clipshare.exception.NotFoundException;
import com.exentricdev.clipshare.repository.VideoRepository;
import jakarta.transaction.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {
    private final static int MAX_TITLE_LENGTH = 75;

    private final VideoRepository videoRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public VideoService(VideoRepository videoRepository, RabbitTemplate rabbitTemplate) {
        this.videoRepository = videoRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public Video uploadVideo(MultipartFile file, String uploaderId) {
        System.out.println("Received upload request from uploaderId: " + uploaderId);

        // Check if uploader is already processing another video
        long processingCount = videoRepository.countByUploaderIdAndIsProcessed(uploaderId, false);

        if (processingCount >= 1) {
            throw new ConflictException("You can only upload one video at a time.");
        }

        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        // Check file type
        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("video/")) {
            throw new BadRequestException("Invalid file type. Only video files are allowed.");
        }

        String extension = getFileExtension(file);

        if (extension.isEmpty()) {
            throw new BadRequestException("File must have an extension");
        }

        String fileTitle = Optional.ofNullable(file.getOriginalFilename())
                .map(name -> {
                    int dotIndex = name.lastIndexOf('.');

                    String base = (dotIndex != -1) ? name.substring(0, dotIndex) : name;
                    return base.substring(0, Math.min(base.length(), MAX_TITLE_LENGTH));
                })
                .orElse("untitled");

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String storedFileName = uuid + extension;

        // Create Video entity
        Video video = new Video(
                uploaderId,
                fileTitle,
                null,
                false,
                storedFileName,
                file.getSize(),
                contentType,
                Instant.now()
        );

        video.setId(uuid);
        videoRepository.save(video);

        Path uploadPath = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();

        if (!uploadPath.toFile().exists()) {
            uploadPath.toFile().mkdirs();
        }

        // Raw upload path
        Path rawUploadPath = uploadPath.resolve("raw");

        if (!rawUploadPath.toFile().exists()) {
            rawUploadPath.toFile().mkdirs();
        }

        // Processed upload path
        Path processedUploadPath = uploadPath.resolve("processed");

        if (!processedUploadPath.toFile().exists()) {
            processedUploadPath.toFile().mkdirs();
        }

        // Save file to disk
        try {
            Path targetLocation = rawUploadPath.resolve(storedFileName);
            file.transferTo(targetLocation.toFile());

            String relativeInputPath = uploadDir + "/raw/" + storedFileName;
            String relativeOutputPath = uploadDir + "/processed/" + storedFileName;

            // Update video URL to point to raw file and save
            video.setUrl(relativeInputPath);
            videoRepository.save(video);

            // Send message to RabbitMQ for processing
            rabbitTemplate.convertAndSend("video-transcoder", new TranscodeJob(
                    video.getId(),
                    relativeInputPath,
                    relativeOutputPath
            ));

            return video;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload video", e);
        }
    }

    public boolean deleteVideo(String videoId, String requesterId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new NotFoundException("Video with ID " + videoId + " not found."));

        if (!video.getUploaderId().equals(requesterId)) {
            throw new ForbiddenException("You are not authorized to delete this video.");
        }

        // Delete file from disk
        Path filePath = Path.of(video.getUrl()).toAbsolutePath().normalize();
        try {
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete video file", e);
        }

        // Delete from database
        videoRepository.deleteById(videoId);
        return true;
    }

    public Video updateVideo(String videoId, String requesterId, VideoPatch updates) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new NotFoundException("Video with ID " + videoId + " not found."));

        if (!video.getUploaderId().equals(requesterId)) {
            throw new ForbiddenException("You are not authorized to update this video.");
        }

        // Handle title update
        if (updates.title() != null && !updates.title().isBlank()) {
            // Limit title length
            if (updates.title().length() > MAX_TITLE_LENGTH) {
                throw new BadRequestException("Title cannot exceed " + MAX_TITLE_LENGTH + " characters.");
            }

            video.setTitle(updates.title());
        }

        videoRepository.save(video);
        return video;
    }

    private String getFileExtension(MultipartFile file) {
        String original = file.getOriginalFilename();

        if (original == null) {
            throw new IllegalArgumentException("File name is missing");
        }

        int dotIndex = original.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == original.length() - 1) {
            return ""; // no extension
        }

        return original.substring(dotIndex).toLowerCase();
    }

    public Video getVideoById(String id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video with ID " + id + " not found."));
    }

    public List<Video> getVideosByUploader(String uploaderId) {
        return videoRepository.findByUploaderId(uploaderId);
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }
}
