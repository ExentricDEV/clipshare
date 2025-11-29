package com.exentricdev.transcoder_worker.dto;

public record TranscodeJob(
    String videoId,
    String inputPath,
    String outputPath
) {
}
