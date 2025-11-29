package com.exentricdev.clipshare.DTO;

public record TranscodeJob(
    String videoId,
    String inputPath,
    String outputPath
) {
}
