package com.queuecut.repository;

import com.queuecut.entity.BarberAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BarberAccountRepository extends JpaRepository<BarberAccount, UUID> {
    Optional<BarberAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
