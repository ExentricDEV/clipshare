package com.exentricdev.clipshare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Server-sent event streams, several per user (one per open tab).
 *
 * Streams time out after 30 minutes; the client reconnects with a fresh access token, so a stream never
 * outlives the login that opened it by much. A heartbeat comment every 20 seconds keeps idle streams
 * from being cut by proxy read timeouts (nginx's is 300s here) and detects dead connections early.
 *
 * Limitation: emitters live in this JVM's memory. With more than one backend instance, events would need to be
 * fanned out to every instance (e.g. a RabbitMQ fanout exchange or Redis pub/sub).
 */
@Service
public class EmitterService {
    private static final Logger log = LoggerFactory.getLogger(EmitterService.class);
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitterForUser(String userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        try {
            // flush the response headers right away so the client knows the stream is open
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void notifyUser(String userId, String eventName, Object payload) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    public int openStreams() {
        return userEmitters.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(String userId, SseEmitter emitter) {
        userEmitters.computeIfPresent(userId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
        log.debug("SSE stream closed for user {}", userId);
    }
}
