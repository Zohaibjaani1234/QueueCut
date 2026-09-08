package com.queuecut.service;

import com.queuecut.dto.settings.SettingsResponse;
import com.queuecut.dto.settings.UpdateSettingsRequest;
import com.queuecut.entity.Settings;
import com.queuecut.repository.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        Settings settings = getOrCreateSettings();
        return new SettingsResponse(settings.getAvgHaircutMin(), settings.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public int getAvgHaircutMinutes() {
        return getOrCreateSettings().getAvgHaircutMin();
    }

    @Transactional
    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        Settings settings = getOrCreateSettings();
        settings.setAvgHaircutMin(request.getAvgHaircutMin());
        Settings saved = settingsRepository.save(settings);
        return new SettingsResponse(saved.getAvgHaircutMin(), saved.getUpdatedAt());
    }

    private Settings getOrCreateSettings() {
        return settingsRepository.findById((short) 1).orElseGet(() -> {
            Settings defaultSettings = new Settings();
            defaultSettings.setId((short) 1);
            defaultSettings.setAvgHaircutMin(20);
            return settingsRepository.save(defaultSettings);
        });
    }
}
