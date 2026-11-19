package com.exentricdev.transcoder_worker.dto;

import java.util.List;

/**
 * Published once a video's HLS ladder is in place.
 *
 * @param masterPlaylistPath the master.m3u8 that lists every rendition
 * @param posterPath a JPEG frame used as the thumbnail
 * @param renditions heights of the renditions produced, highest first (e.g. [1080, 720, 480])
 */
public record ProcessedVideo(
    String videoId,
    String masterPlaylistPath,
    String posterPath,
    double durationSeconds,
    List<Integer> renditions
) {
}
