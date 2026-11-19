package com.exentricdev.clipshare.DTO;

import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.entity.Visibility;

import java.util.List;

/**
 * @param posterUrl short-lived signed thumbnail URL, null until the video is READY
 */
public record VideoResponse(
        String id,
        String title,
        long size,
        boolean isProcessed,
        VideoStatus status,
        int progress,
        String failureReason,
        Visibility visibility,
        Double durationSeconds,
        List<Integer> renditions,
        String posterUrl
) {
}
