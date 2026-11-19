package com.exentricdev.clipshare.repository;

import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface VideoRepository extends JpaRepository<Video, String> {
    List<Video> findByUploaderIdOrderByUploadedAtDesc(String uploaderId);

    List<Video> findAllByOrderByUploadedAtDesc();

    List<Video> findByVisibilityAndStatusOrderByUploadedAtDesc(Visibility visibility, VideoStatus status);

    long countByUploaderIdAndStatusIn(String uploaderId, Collection<VideoStatus> statuses);

    /**
     * Records progress only while the job is still active and only forwards, in one statement: a late or
     * out-of-order progress message can't move a READY or FAILED video back to PROCESSING.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Video v SET v.progress = :progress, v.status = com.exentricdev.clipshare.entity.VideoStatus.PROCESSING "
            + "WHERE v.id = :id AND v.progress < :progress AND v.status IN "
            + "(com.exentricdev.clipshare.entity.VideoStatus.PENDING, com.exentricdev.clipshare.entity.VideoStatus.PROCESSING)")
    int recordProgress(@Param("id") String id, @Param("progress") int progress);
}
