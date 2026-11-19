package com.exentricdev.transcoder_worker.dto;

/**
 * A request to transcode one upload.
 *
 * @param inputPath raw upload on the shared volume
 * @param outputDir directory the HLS ladder is published to (written to a temp dir first, then renamed into place)
 */
public record TranscodeJob(
    String videoId,
    String inputPath,
    String outputDir
) {
}
