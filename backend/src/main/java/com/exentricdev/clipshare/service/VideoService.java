package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.TranscodeJob;
import com.exentricdev.clipshare.DTO.VideoPatch;
import com.exentricdev.clipshare.config.RabbitConfig;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.entity.Visibility;
import com.exentricdev.clipshare.exception.BadRequestException;
import com.exentricdev.clipshare.exception.ConflictException;
import com.exentricdev.clipshare.exception.ForbiddenException;
import com.exentricdev.clipshare.exception.NotFoundException;
import com.exentricdev.clipshare.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private static final int MAX_TITLE_LENGTH = 75;
    private static final EnumSet<VideoStatus> ACTIVE = EnumSet.of(VideoStatus.PENDING, VideoStatus.PROCESSING);

    private final VideoRepository videoRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public VideoService(VideoRepository videoRepository, RabbitTemplate rabbitTemplate) {
        this.videoRepository = videoRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Saves the upload and queues it for transcoding.
     *
     * The job is published only after the database transaction commits. Publishing inside the transaction
     * would let a fast worker finish before the row exists (or publish a job for a row that then rolls back).
     */
    @Transactional
    public Video uploadVideo(MultipartFile file, String uploaderId) {
        // one active transcode per user; FAILED and READY videos don't count, so a failure never locks you out
        if (videoRepository.countByUploaderIdAndStatusIn(uploaderId, ACTIVE) >= 1) {
            throw new ConflictException("You can only process one video at a time.");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new BadRequestException("Invalid file type. Only video files are allowed.");
        }
        String extension = getFileExtension(file);
        if (extension.isEmpty()) {
            throw new BadRequestException("File must have an extension");
        }

        String title = Optional.ofNullable(file.getOriginalFilename())
                .map(name -> {
                    int dot = name.lastIndexOf('.');
                    String base = dot != -1 ? name.substring(0, dot) : name;
                    return base.substring(0, Math.min(base.length(), MAX_TITLE_LENGTH));
                })
                .orElse("untitled");

        String id = UUID.randomUUID().toString().replace("-", "");
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path rawFile = root.resolve("raw").resolve(id + extension);
        Path outputDir = root.resolve("processed").resolve(id);

        try {
            Files.createDirectories(rawFile.getParent());
            Files.createDirectories(outputDir.getParent());
            file.transferTo(rawFile.toFile());
        } catch (IOException e) {
            deleteQuietly(rawFile);
            throw new IllegalStateException("Failed to store upload", e);
        }

        Video video = new Video(uploaderId, title, rawFile.toString(), false, id + extension, file.getSize(), contentType, Instant.now());
        video.setId(id);
        video.setStatus(VideoStatus.PENDING);
        videoRepository.save(video);

        TranscodeJob job = new TranscodeJob(id, rawFile.toString(), outputDir.toString());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(RabbitConfig.JOBS, job);
                log.info("Queued transcode for videoId={}", id);
            }

            @Override
            public void afterCompletion(int status) {
                // the row never made it into the database, so don't leave the file behind
                if (status == STATUS_ROLLED_BACK) deleteQuietly(rawFile);
            }
        });
        return video;
    }

    @Transactional
    public void deleteVideo(String videoId, String requesterId, boolean requesterIsAdmin) {
        Video video = getVideoById(videoId);
        requireOwnerOrAdmin(video, requesterId, requesterIsAdmin, "delete");

        videoRepository.delete(video);
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        // the transcoded HLS directory, plus the raw upload if it's still around (pending or failed)
        deleteRecursively(root.resolve("processed").resolve(videoId));
        if (video.getUrl() != null) deleteQuietly(Path.of(video.getUrl()));
        deleteQuietly(root.resolve("raw").resolve(video.getStoredFileName()));
    }

    @Transactional
    public Video updateVideo(String videoId, String requesterId, boolean requesterIsAdmin, VideoPatch updates) {
        Video video = getVideoById(videoId);
        requireOwnerOrAdmin(video, requesterId, requesterIsAdmin, "update");

        if (updates.title() != null && !updates.title().isBlank()) {
            if (updates.title().length() > MAX_TITLE_LENGTH) {
                throw new BadRequestException("Title cannot exceed " + MAX_TITLE_LENGTH + " characters.");
            }
            video.setTitle(updates.title());
        }
        if (updates.visibility() != null) {
            video.setVisibility(updates.visibility());
        }
        return videoRepository.save(video);
    }

    /**
     * Who may watch a video: the uploader and admins always; anyone else only if it isn't PRIVATE.
     * Unknown and forbidden both return 404, so private video ids can't be probed for existence.
     */
    public Video getWatchableVideo(String id, String requesterId, boolean requesterIsAdmin) {
        Video video = getVideoById(id);
        boolean owner = video.getUploaderId().equals(requesterId);
        if (video.getVisibility() == Visibility.PRIVATE && !owner && !requesterIsAdmin) {
            throw new NotFoundException("Video with ID " + id + " not found.");
        }
        return video;
    }

    public Video getVideoById(String id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video with ID " + id + " not found."));
    }

    public List<Video> getVideosByUploader(String uploaderId) {
        return videoRepository.findByUploaderIdOrderByUploadedAtDesc(uploaderId);
    }

    public List<Video> getPublicVideos() {
        return videoRepository.findByVisibilityAndStatusOrderByUploadedAtDesc(Visibility.PUBLIC, VideoStatus.READY);
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAllByOrderByUploadedAtDesc();
    }

    private static void requireOwnerOrAdmin(Video video, String requesterId, boolean admin, String action) {
        if (!admin && !video.getUploaderId().equals(requesterId)) {
            throw new ForbiddenException("You are not authorized to " + action + " this video.");
        }
    }

    private String getFileExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) {
            throw new BadRequestException("File name is missing");
        }
        int dot = original.lastIndexOf('.');
        if (dot == -1 || dot == original.length() - 1) {
            return "";
        }
        String ext = original.substring(dot).toLowerCase();
        // the extension ends up in a file name on disk, so keep it to plain characters
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }

    static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(VideoService::deleteQuietly);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", dir, e.getMessage());
        }
    }
}
