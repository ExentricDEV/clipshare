package com.exentricdev.clipshare.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Signs short-lived media URLs.
 *
 * A {@code <video>} element (and an HLS player fetching segments) can't send an Authorization header, so
 * access is checked once when the client asks for a playback URL, and the URL then carries proof:
 * {@code ?exp=<unix seconds>&sig=<HMAC-SHA256(videoId:exp)>}. Every playlist, segment, and poster request is
 * verified against that signature, so a URL can't be forged, extended, or reused for another video.
 */
@Component
public class PlaybackSigner {
    private final byte[] key;
    private final Duration ttl;
    private final Clock clock;

    public PlaybackSigner(@Value("${app.playback.signing-key:}") String configuredKey,
                          @Value("${app.playback.url-ttl:4h}") Duration ttl) {
        this(configuredKey, ttl, Clock.systemUTC());
    }

    PlaybackSigner(String configuredKey, Duration ttl, Clock clock) {
        if (configuredKey == null || configuredKey.isBlank()) {
            // fine for a single instance; with several backends, set app.playback.signing-key so they agree
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
        } else {
            this.key = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    /** "exp=...&sig=..." for this video, valid for the configured TTL */
    public String sign(String videoId) {
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return "exp=" + exp + "&sig=" + hmac(videoId + ":" + exp);
    }

    public boolean verify(String videoId, String exp, String sig) {
        if (exp == null || sig == null || !exp.matches("\\d{1,12}")) return false;
        if (Long.parseLong(exp) < clock.instant().getEpochSecond()) return false;
        byte[] expected = hmac(videoId + ":" + exp).getBytes(StandardCharsets.US_ASCII);
        // constant-time comparison, so the signature can't be guessed byte by byte from response timing
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
