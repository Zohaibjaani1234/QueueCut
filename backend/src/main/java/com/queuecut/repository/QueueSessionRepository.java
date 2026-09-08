package com.queuecut.repository;

import com.queuecut.entity.QueueSession;
import com.queuecut.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueSessionRepository extends JpaRepository<QueueSession, UUID> {

    Optional<QueueSession> findBySessionDateAndStatus(LocalDate date, SessionStatus status);

    Optional<QueueSession> findBySessionDate(LocalDate date);

    /**
     * Atomically increments last_queue_number and returns the new value.
     * This is the concurrency-safe queue number generation mechanism.
     * Uses a single UPDATE statement — serialized at the PostgreSQL row level.
     * Returns 0 if session not found or not OPEN.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE queue_session
            SET last_queue_number = last_queue_number + 1,
                updated_at = now()
            WHERE id = :sessionId AND status = 'OPEN'
            """, nativeQuery = true)
    int incrementQueueNumber(@Param("sessionId") UUID sessionId);

    @Query("SELECT qs.lastQueueNumber FROM QueueSession qs WHERE qs.id = :sessionId")
    Optional<Integer> getLastQueueNumber(@Param("sessionId") UUID sessionId);
}
