package com.exentricdev.transcoder_worker.dto;

/** Progress for one job, sent roughly once a second while ffmpeg runs. */
public record TranscodeProgress(String videoId, int percent) {
}
