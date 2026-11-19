package com.exentricdev.clipshare.DTO;

/**
 * @param inputPath raw upload on the shared volume
 * @param outputDir directory the worker publishes the HLS ladder to
 */
public record TranscodeJob(
    String videoId,
    String inputPath,
    String outputDir
) {
}
