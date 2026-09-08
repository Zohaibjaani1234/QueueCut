package com.queuecut.config;

import com.queuecut.entity.BarberAccount;
import com.queuecut.entity.Settings;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on application startup to ensure seed data is initialized.
 * - Creates the barber account if it doesn't exist.
 * - Updates the password hash if it's a placeholder.
 * - Ensures settings row exists.
 *
 * Uses BARBER_INITIAL_PASSWORD env variable — never hard-codes credentials.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final BarberAccountRepository barberAccountRepository;
    private final SettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${barber.initial-password}")
    private String barberInitialPassword;

    private static final String BARBER_USERNAME = "arslan";
    private static final String BARBER_FULL_NAME = "Muhammad Arslan";
    private static final String BARBER_CONTACT = "03458717687";
    private static final String PLACEHOLDER_HASH = "$2a$12$PLACEHOLDER_REPLACED_BY_STARTUP";

    public DataInitializer(BarberAccountRepository barberAccountRepository,
                           SettingsRepository settingsRepository,
                           PasswordEncoder passwordEncoder) {
        this.barberAccountRepository = barberAccountRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initializeBarberAccount();
        initializeSettings();
    }

    private void initializeBarberAccount() {
        if (!barberAccountRepository.existsByUsername(BARBER_USERNAME)) {
            BarberAccount barber = new BarberAccount();
            barber.setUsername(BARBER_USERNAME);
            barber.setPasswordHash(passwordEncoder.encode(barberInitialPassword));
            barber.setFullName(BARBER_FULL_NAME);
            barber.setContactNumber(BARBER_CONTACT);
            barber.setRole("ROLE_BARBER");
            barberAccountRepository.save(barber);
            log.info("Barber account '{}' created.", BARBER_USERNAME);
        } else {
            // Update hash if still placeholder
            barberAccountRepository.findByUsername(BARBER_USERNAME).ifPresent(barber -> {
                if (PLACEHOLDER_HASH.equals(barber.getPasswordHash())) {
                    barber.setPasswordHash(passwordEncoder.encode(barberInitialPassword));
                    barberAccountRepository.save(barber);
                    log.info("Barber password hash updated from placeholder.");
                }
            });
        }
    }

    private void initializeSettings() {
        if (!settingsRepository.existsById((short) 1)) {
            Settings settings = new Settings();
            settings.setId((short) 1);
            settings.setAvgHaircutMin(20);
            settingsRepository.save(settings);
            log.info("Default settings initialized (avg haircut: 20 min).");
        }
    }
}
