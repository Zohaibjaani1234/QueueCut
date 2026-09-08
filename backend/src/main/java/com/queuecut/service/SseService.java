package com.queuecut.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Events (SSE) connections and broadcasts real-time queue events.
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final Long DEFAULT_TIMEOUT = 1800000L; // 30 minutes

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers a new SSE subscriber client.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitters.add(emitter);
        log.debug("New SSE client subscribed. Total clients: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE client completed. Total clients: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE client timed out. Total clients: {}", emitters.size());
        });

        emitter.onError(throwable -> {
            emitters.remove(emitter);
            log.debug("SSE client error. Total clients: {}", emitters.size());
        });

        return emitter;
    }

    /**
     * Broadcasts an event with payload to all connected SSE clients.
     */
    public void broadcast(String eventName, Object data) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Removed {} dead SSE emitters. Remaining: {}", deadEmitters.size(), emitters.size());
        }
    }

    /**
     * Periodic heartbeat ping sent every 25 seconds to keep intermediary proxies from timing out.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (!emitters.isEmpty()) {
            broadcast("HEARTBEAT", Map.of("ping", Instant.now().toEpochMilli()));
        }
    }
}
