package com.exentricdev.clipshare.DTO;

import java.util.List;

public record ProcessedVideo(
    String videoId,
    String masterPlaylistPath,
    String posterPath,
    double durationSeconds,
    List<Integer> renditions
) {
}
