package com.queuecut.dto.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateSettingsRequest {

    @NotNull(message = "avgHaircutMin is required")
    @Min(value = 5, message = "Haircut duration must be at least 5 minutes")
    @Max(value = 120, message = "Haircut duration cannot exceed 120 minutes")
    private Integer avgHaircutMin;

    public UpdateSettingsRequest() {
    }

    public UpdateSettingsRequest(Integer avgHaircutMin) {
        this.avgHaircutMin = avgHaircutMin;
    }

    public Integer getAvgHaircutMin() {
        return avgHaircutMin;
    }

    public void setAvgHaircutMin(Integer avgHaircutMin) {
        this.avgHaircutMin = avgHaircutMin;
    }
}
