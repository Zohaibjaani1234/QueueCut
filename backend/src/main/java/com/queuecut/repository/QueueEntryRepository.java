package com.queuecut.repository;

import com.queuecut.entity.QueueEntry;
import com.queuecut.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    List<QueueEntry> findBySessionIdOrderByQueueNumberAsc(UUID sessionId);

    List<QueueEntry> findBySessionIdAndStatusInOrderByQueueNumberAsc(UUID sessionId, List<QueueStatus> statuses);

    Optional<QueueEntry> findBySessionIdAndStatus(UUID sessionId, QueueStatus status);

    Optional<QueueEntry> findByStudentToken(UUID studentToken);

    /**
     * Check if a student already has an active entry in this session.
     * Active = WAITING, ALMOST_READY, or CURRENT.
     */
    @Query("""
            SELECT COUNT(qe) > 0 FROM QueueEntry qe
            WHERE qe.session.id = :sessionId
              AND qe.studentId = :studentId
              AND qe.status IN ('WAITING', 'ALMOST_READY', 'CURRENT')
            """)
    boolean existsActiveEntryForStudent(@Param("sessionId") UUID sessionId,
                                        @Param("studentId") String studentId);

    /**
     * Count how many people are ahead of the given queue number with active statuses.
     */
    @Query("""
            SELECT COUNT(qe) FROM QueueEntry qe
            WHERE qe.session.id = :sessionId
              AND qe.queueNumber < :myQueueNumber
              AND qe.status IN ('WAITING', 'ALMOST_READY', 'CURRENT')
            """)
    long countPeopleAhead(@Param("sessionId") UUID sessionId,
                          @Param("myQueueNumber") int myQueueNumber);

    /**
     * Count total active entries in a session (all people waiting).
     */
    @Query("""
            SELECT COUNT(qe) FROM QueueEntry qe
            WHERE qe.session.id = :sessionId
              AND qe.status IN ('WAITING', 'ALMOST_READY', 'CURRENT')
            """)
    long countActiveEntries(@Param("sessionId") UUID sessionId);

    /**
     * Find the next entry to be called (lowest queue number, active status, excluding CURRENT).
     */
    @Query("""
            SELECT qe FROM QueueEntry qe
            WHERE qe.session.id = :sessionId
              AND qe.status IN ('WAITING', 'ALMOST_READY')
            ORDER BY qe.queueNumber ASC
            LIMIT 1
            """)
    Optional<QueueEntry> findNextWaitingEntry(@Param("sessionId") UUID sessionId);

    /**
     * Find the entry that should become ALMOST_READY (the one right after the next CURRENT).
     */
    @Query("""
            SELECT qe FROM QueueEntry qe
            WHERE qe.session.id = :sessionId
              AND qe.status IN ('WAITING', 'ALMOST_READY')
            ORDER BY qe.queueNumber ASC
            LIMIT 1
            OFFSET 1
            """)
    Optional<QueueEntry> findAlmostReadyCandidate(@Param("sessionId") UUID sessionId);
}
