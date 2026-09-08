package com.queuecut.repository;

import com.queuecut.entity.Settings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettingsRepository extends JpaRepository<Settings, Short> {
    // Single-row table — use findById((short) 1)
}
