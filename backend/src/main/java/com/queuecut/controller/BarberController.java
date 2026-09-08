package com.queuecut.controller;

import com.queuecut.dto.queue.CallNextResponse;
import com.queuecut.dto.queue.QueueEntryDto;
import com.queuecut.dto.queue.QueueSessionDto;
import com.queuecut.service.BarberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/barber")
public class BarberController {

    private final BarberService barberService;

    public BarberController(BarberService barberService) {
        this.barberService = barberService;
    }

    /**
     * Opens today's queue session.
     */
    @PostMapping("/session/open")
    public ResponseEntity<QueueSessionDto> openSession(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(barberService.openSession(userDetails.getUsername()));
    }

    /**
     * Closes today's queue session.
     */
    @PostMapping("/session/close")
    public ResponseEntity<QueueSessionDto> closeSession(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(barberService.closeSession(userDetails.getUsername()));
    }

    /**
     * Get the details of today's queue session.
     */
    @GetMapping("/session/current")
    public ResponseEntity<QueueSessionDto> getCurrentSession() {
        return ResponseEntity.ok(barberService.getCurrentSession());
    }

    /**
     * Calls the next student in line (completing the current one if present).
     */
    @PostMapping("/queue/call-next")
    public ResponseEntity<CallNextResponse> callNext(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(barberService.callNext(userDetails.getUsername()));
    }

    /**
     * Explicitly marks a queue entry as completed.
     */
    @PostMapping("/queue/{entryId}/complete")
    public ResponseEntity<QueueEntryDto> completeEntry(@PathVariable UUID entryId) {
        return ResponseEntity.ok(barberService.completeEntry(entryId));
    }

    /**
     * Marks a queue entry as skipped (no-show).
     */
    @PostMapping("/queue/{entryId}/skip")
    public ResponseEntity<QueueEntryDto> skipEntry(@PathVariable UUID entryId) {
        return ResponseEntity.ok(barberService.skipEntry(entryId));
    }

    /**
     * Retrieves queue entries for the session, optionally filtered by ?status=ACTIVE.
     */
    @GetMapping("/queue/entries")
    public ResponseEntity<List<QueueEntryDto>> getEntries(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(barberService.getSessionEntries(status));
    }
}
