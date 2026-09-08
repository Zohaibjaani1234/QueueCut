package com.queuecut.dto.settings;

import java.time.Instant;

public class SettingsResponse {

    private Integer avgHaircutMin;
    private Instant updatedAt;

    public SettingsResponse() {
    }

    public SettingsResponse(Integer avgHaircutMin, Instant updatedAt) {
        this.avgHaircutMin = avgHaircutMin;
        this.updatedAt = updatedAt;
    }

    public Integer getAvgHaircutMin() {
        return avgHaircutMin;
    }

    public void setAvgHaircutMin(Integer avgHaircutMin) {
        this.avgHaircutMin = avgHaircutMin;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
