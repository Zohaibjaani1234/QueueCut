package com.queuecut.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Single-row table for application-wide configuration.
 * id is always 1, enforced by a CHECK constraint in the schema.
 */
@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "id", nullable = false)
    private Short id = 1;

    /**
     * Average haircut duration in minutes. Used to calculate estimated wait time.
     * Range: 5–120 minutes.
     */
    @Column(name = "avg_haircut_min", nullable = false)
    private Integer avgHaircutMin = 20;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Settings() {
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = Instant.now();
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
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
