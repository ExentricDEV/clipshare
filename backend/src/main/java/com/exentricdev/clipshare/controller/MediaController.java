package com.exentricdev.clipshare.controller;

import com.exentricdev.clipshare.entity.Video;
import com.exentricdev.clipshare.entity.VideoStatus;
import com.exentricdev.clipshare.exception.NotFoundException;
import com.exentricdev.clipshare.security.PlaybackSigner;
import com.exentricdev.clipshare.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Serves media files to anyone holding a valid signed URL (see {@link PlaybackSigner}).
 *
 * HLS playlists reference their segments and variant playlists by relative path, and a player requests
 * those without our query string. So playlists are rewritten on the way out: every URI line gets the
 * same {@code exp}/{@code sig} appended, and the whole ladder is then playable with one signature.
 */
@RestController
@RequestMapping("/api/videos/{id}")
public class MediaController {
    private static final MediaType M3U8 = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    private static final MediaType TS = MediaType.parseMediaType("video/mp2t");
    /** v0/index.m3u8, v1/seg_004.ts, master.m3u8 — nothing else (no "..", no absolute paths) */
    private static final Pattern HLS_FILE = Pattern.compile("(v\\d{1,2}/)?[A-Za-z0-9_]{1,40}\\.(m3u8|ts)");

    private final VideoService videoService;
    private final PlaybackSigner signer;

    public MediaController(VideoService videoService, PlaybackSigner signer) {
        this.videoService = videoService;
        this.signer = signer;
    }

    @GetMapping("/hls/**")
    public ResponseEntity<?> hls(@PathVariable String id, @RequestParam(required = false) String exp,
                                 @RequestParam(required = false) String sig, HttpServletRequest request) throws IOException {
        Video video = authorize(id, exp, sig);
        if (!video.isHls()) throw new NotFoundException("Not an HLS video");

        String prefix = "/api/videos/" + id + "/hls/";
        String uri = request.getRequestURI();
        String relative = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
        if (!HLS_FILE.matcher(relative).matches()) throw new NotFoundException("No such file");

        Path dir = Path.of(video.getUrl()).getParent().toAbsolutePath().normalize();
        Path file = dir.resolve(relative).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) throw new NotFoundException("No such file");

        if (relative.endsWith(".m3u8")) {
            String query = "exp=" + exp + "&sig=" + sig;
            String body = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    // URI lines are the non-empty lines that aren't tags; ffmpeg on Windows writes '\' separators
                    .map(line -> line.isBlank() || line.startsWith("#") ? line : line.replace('\\', '/') + "?" + query)
                    .collect(Collectors.joining("\n", "", "\n"));
            return ResponseEntity.ok()
                    .contentType(M3U8)
                    .cacheControl(CacheControl.noStore())
                    .body(body);
        }
        // segments never change once published, so the browser can cache them for the life of the URL
        return ResponseEntity.ok()
                .contentType(TS)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(new FileSystemResource(file));
    }

    @GetMapping("/poster.jpg")
    public ResponseEntity<Resource> poster(@PathVariable String id, @RequestParam(required = false) String exp,
                                           @RequestParam(required = false) String sig) {
        Video video = authorize(id, exp, sig);
        if (video.getPosterPath() == null || !Files.isRegularFile(Path.of(video.getPosterPath()))) {
            throw new NotFoundException("No poster");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(new FileSystemResource(Path.of(video.getPosterPath())));
    }

    /** Legacy single-MP4 videos processed before the HLS pipeline. Spring handles Range requests for seeking. */
    @GetMapping("/file")
    public ResponseEntity<Resource> file(@PathVariable String id, @RequestParam(required = false) String exp,
                                         @RequestParam(required = false) String sig) {
        Video video = authorize(id, exp, sig);
        if (video.isHls() || video.getUrl() == null) throw new NotFoundException("No such file");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(video.getContentType()))
                .body(new FileSystemResource(Path.of(video.getUrl())));
    }

    private Video authorize(String id, String exp, String sig) {
        if (!signer.verify(id, exp, sig)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired link");
        }
        Video video = videoService.getVideoById(id);
        if (video.getStatus() != VideoStatus.READY) throw new NotFoundException("Video is not ready");
        return video;
    }
}
