package com.queuecut.controller;

import com.queuecut.dto.settings.SettingsResponse;
import com.queuecut.dto.settings.UpdateSettingsRequest;
import com.queuecut.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Public read-only endpoint for current settings (haircut duration).
     */
    @GetMapping
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    /**
     * Barber-only endpoint to update average haircut duration.
     */
    @PutMapping
    public ResponseEntity<SettingsResponse> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }
}
