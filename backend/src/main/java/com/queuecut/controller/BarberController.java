package com.queuecut.controller;

import com.queuecut.dto.auth.ChangePasswordRequest;
import com.queuecut.dto.queue.CallNextResponse;
import com.queuecut.dto.queue.QueueEntryDto;
import com.queuecut.dto.queue.QueueSessionDto;
import com.queuecut.service.AuthService;
import com.queuecut.service.BarberService;
import jakarta.validation.Valid;
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
    private final AuthService authService;

    public BarberController(BarberService barberService, AuthService authService) {
        this.barberService = barberService;
        this.authService = authService;
    }

    /**
     * Changes the logged-in barber's password (requires the current password).
     */
    @PostMapping("/account/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password changed successfully."
        ));
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
    public ResponseEntity<CallNextResponse> callNext(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "expectedCurrent", required = false) String expectedCurrent) {
        // expectedCurrent: entry id shown in the chair, or "none" when the chair is shown empty.
        // Omitted = no double-tap protection (older clients).
        boolean check = expectedCurrent != null;
        UUID expectedId = (check && !"none".equals(expectedCurrent)) ? UUID.fromString(expectedCurrent) : null;
        return ResponseEntity.ok(barberService.callNext(userDetails.getUsername(), expectedId, check));
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
