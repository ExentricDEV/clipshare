package com.exentricdev.clipshare.service;

import com.exentricdev.clipshare.DTO.PlaybackResponse;
import com.exentricdev.clipshare.DTO.VideoResponse;
import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.security.PlaybackSigner;
import org.springframework.stereotype.Component;

/** Builds API responses, including the signed media URLs a browser needs. */
@Component
public class VideoMapper {
    /** public path prefix of the API as the browser sees it (nginx proxies /api to the backend) */
    private static final String API = "/api/videos/";

    private final PlaybackSigner signer;

    public VideoMapper(PlaybackSigner signer) {
        this.signer = signer;
    }

    public VideoResponse toResponse(Video v) {
        boolean ready = v.getStatus() == VideoStatus.READY;
        return new VideoResponse(
                v.getId(),
                v.getTitle(),
                v.getSize(),
                ready,
                v.getStatus(),
                v.getProgress(),
                v.getFailureReason(),
                v.getVisibility(),
                v.getDurationSeconds(),
                v.getRenditions(),
                ready && v.getPosterPath() != null ? posterUrl(v) : null);
    }

    public PlaybackResponse toPlayback(Video v) {
        String query = signer.sign(v.getId());
        String url = v.isHls()
                ? API + v.getId() + "/hls/master.m3u8?" + query
                : API + v.getId() + "/file?" + query;
        return new PlaybackResponse(v.getId(), v.getTitle(), url, v.isHls() ? "hls" : "mp4",
                v.getPosterPath() != null ? posterUrl(v) : null);
    }

    private String posterUrl(Video v) {
        return API + v.getId() + "/poster.jpg?" + signer.sign(v.getId());
    }
}
