package com.exentricdev.clipshare.DTO;

/**
 * Where to play a video from.
 *
 * @param url signed, short-lived URL: an HLS master playlist, or an MP4 for videos processed before HLS
 * @param type "hls" or "mp4"
 */
public record PlaybackResponse(String id, String title, String url, String type, String posterUrl) {
}
