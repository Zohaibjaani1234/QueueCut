package com.queuecut.controller;

import com.queuecut.dto.queue.JoinQueueRequest;
import com.queuecut.dto.queue.JoinQueueResponse;
import com.queuecut.dto.queue.MyStatusResponse;
import com.queuecut.dto.queue.QueueStatusResponse;
import com.queuecut.service.QueueService;
import com.queuecut.service.SseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final SseService sseService;

    public QueueController(QueueService queueService, SseService sseService) {
        this.queueService = queueService;
        this.sseService = sseService;
    }

    /**
     * Public endpoint to view general queue status and estimated wait time.
     */
    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> getStatus() {
        return ResponseEntity.ok(queueService.getPublicStatus());
    }

    /**
     * Public endpoint for students to join today's queue.
     */
    @PostMapping("/join")
    public ResponseEntity<JoinQueueResponse> joinQueue(@Valid @RequestBody JoinQueueRequest request) {
        JoinQueueResponse response = queueService.joinQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticated student endpoint to query personal ticket status.
     * Uses X-Student-Token header for authorization.
     */
    @GetMapping("/my-status/{entryId}")
    public ResponseEntity<MyStatusResponse> getMyStatus(
            @PathVariable UUID entryId,
            @RequestHeader(value = "X-Student-Token", required = false) UUID studentToken) {
        return ResponseEntity.ok(queueService.getMyStatus(entryId, studentToken));
    }

    /**
     * Student cancels their own queue entry before being called.
     */
    @PostMapping("/{entryId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelEntry(
            @PathVariable UUID entryId,
            @RequestHeader(value = "X-Student-Token", required = false) UUID studentToken) {
        queueService.cancelEntry(entryId, studentToken);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Your queue position has been cancelled.",
                "entryId", entryId
        ));
    }

    /**
     * Real-time Server-Sent Events stream for live queue updates.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQueue() {
        SseEmitter emitter = sseService.subscribe();
        try {
            // Push initial state immediately on connection
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(queueService.getPublicStatus()));
        } catch (IOException e) {
            emitter.complete();
        }
        return emitter;
    }
}
