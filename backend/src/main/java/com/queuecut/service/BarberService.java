package com.queuecut.service;

import com.queuecut.dto.queue.CallNextResponse;
import com.queuecut.dto.queue.QueueEntryDto;
import com.queuecut.dto.queue.QueueSessionDto;
import com.queuecut.entity.BarberAccount;
import com.queuecut.entity.QueueEntry;
import com.queuecut.entity.QueueSession;
import com.queuecut.entity.QueueStatus;
import com.queuecut.entity.SessionStatus;
import com.queuecut.exception.EntryNotFoundException;
import com.queuecut.exception.InvalidStateTransitionException;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.repository.QueueEntryRepository;
import com.queuecut.repository.QueueSessionRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BarberService {

    private final QueueSessionRepository queueSessionRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final BarberAccountRepository barberAccountRepository;
    private final QueueService queueService;
    private final SseService sseService;

    public BarberService(QueueSessionRepository queueSessionRepository,
                         QueueEntryRepository queueEntryRepository,
                         BarberAccountRepository barberAccountRepository,
                         QueueService queueService,
                         SseService sseService) {
        this.queueSessionRepository = queueSessionRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.barberAccountRepository = barberAccountRepository;
        this.queueService = queueService;
        this.sseService = sseService;
    }

    @Transactional
    public QueueSessionDto openSession(String username) {
        LocalDate today = LocalDate.now();
        BarberAccount barber = barberAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Barber not found: " + username));

        QueueSession session = queueSessionRepository.findBySessionDate(today).orElseGet(() -> {
            QueueSession newSession = new QueueSession();
            newSession.setBarber(barber);
            newSession.setSessionDate(today);
            newSession.setLastQueueNumber(0);
            return newSession;
        });

        session.setStatus(SessionStatus.OPEN);
        session.setOpenedAt(Instant.now());
        session.setClosedAt(null);
        QueueSession saved = queueSessionRepository.save(session);

        sseService.broadcast("SESSION_STATUS_CHANGED", Map.of(
                "isQueueOpen", true,
                "sessionStatus", "OPEN",
                "message", "The barber queue is now open!"
        ));
        sseService.broadcast("QUEUE_UPDATED", queueService.getPublicStatus());

        return toSessionDto(saved);
    }

    @Transactional
    public QueueSessionDto closeSession(String username) {
        LocalDate today = LocalDate.now();
        QueueSession session = queueSessionRepository.findBySessionDate(today)
                .orElseThrow(() -> new IllegalStateException("No queue session found for today."));

        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(Instant.now());
        QueueSession saved = queueSessionRepository.save(session);

        sseService.broadcast("SESSION_STATUS_CHANGED", Map.of(
                "isQueueOpen", false,
                "sessionStatus", "CLOSED",
                "message", "The barber queue is now closed for today."
        ));
        sseService.broadcast("QUEUE_UPDATED", queueService.getPublicStatus());

        return toSessionDto(saved);
    }

    @Transactional(readOnly = true)
    public QueueSessionDto getCurrentSession() {
        LocalDate today = LocalDate.now();
        QueueSession session = queueSessionRepository.findBySessionDate(today)
                .orElseThrow(() -> new IllegalStateException("No queue session found for today."));

        return toSessionDto(session);
    }

    @Transactional
    public CallNextResponse callNext(String username) {
        LocalDate today = LocalDate.now();
        QueueSession session = queueSessionRepository.findBySessionDate(today)
                .orElseThrow(() -> new IllegalStateException("No queue session found for today."));

        // 1. If someone is currently in the chair, complete them
        Optional<QueueEntry> currentEntryOpt = queueEntryRepository.findBySessionIdAndStatus(session.getId(), QueueStatus.CURRENT);
        if (currentEntryOpt.isPresent()) {
            QueueEntry current = currentEntryOpt.get();
            current.setStatus(QueueStatus.COMPLETED);
            current.setCompletedAt(Instant.now());
            queueEntryRepository.save(current);
        }

        // 2. Find next waiting student
        Optional<QueueEntry> nextWaitingOpt = queueEntryRepository.findNextWaitingEntry(session.getId());
        QueueEntryDto newCurrentDto = null;
        QueueEntryDto almostReadyDto = null;

        if (nextWaitingOpt.isPresent()) {
            QueueEntry next = nextWaitingOpt.get();
            next.setStatus(QueueStatus.CURRENT);
            next.setCalledAt(Instant.now());
            QueueEntry savedCurrent = queueEntryRepository.save(next);
            newCurrentDto = toEntryDto(savedCurrent);

            // 3. Promote the one after that to ALMOST_READY
            Optional<QueueEntry> afterNextOpt = queueEntryRepository.findNextWaitingEntry(session.getId());
            if (afterNextOpt.isPresent()) {
                QueueEntry afterNext = afterNextOpt.get();
                afterNext.setStatus(QueueStatus.ALMOST_READY);
                QueueEntry savedAlmost = queueEntryRepository.save(afterNext);
                almostReadyDto = toEntryDto(savedAlmost);
            }
        }

        long totalWaiting = queueEntryRepository.countActiveEntries(session.getId());
        if (newCurrentDto != null) {
            totalWaiting = Math.max(0, totalWaiting - 1);
        }

        sseService.broadcast("QUEUE_UPDATED", queueService.getPublicStatus());

        return new CallNextResponse(newCurrentDto, almostReadyDto, totalWaiting);
    }

    @Transactional
    public QueueEntryDto completeEntry(UUID entryId) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(EntryNotFoundException::new);

        if (entry.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException(entry.getStatus(), QueueStatus.COMPLETED);
        }

        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(Instant.now());
        QueueEntry saved = queueEntryRepository.save(entry);

        queueService.promoteCandidateIfNeeded(entry.getSession().getId());
        sseService.broadcast("QUEUE_UPDATED", queueService.getPublicStatus());

        return toEntryDto(saved);
    }

    @Transactional
    public QueueEntryDto skipEntry(UUID entryId) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(EntryNotFoundException::new);

        if (entry.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException(entry.getStatus(), QueueStatus.SKIPPED);
        }

        entry.setStatus(QueueStatus.SKIPPED);
        QueueEntry saved = queueEntryRepository.save(entry);

        queueService.promoteCandidateIfNeeded(entry.getSession().getId());
        sseService.broadcast("QUEUE_UPDATED", queueService.getPublicStatus());

        return toEntryDto(saved);
    }

    @Transactional(readOnly = true)
    public List<QueueEntryDto> getSessionEntries(String statusFilter) {
        LocalDate today = LocalDate.now();
        Optional<QueueSession> sessionOpt = queueSessionRepository.findBySessionDate(today);
        if (sessionOpt.isEmpty()) {
            return List.of();
        }

        UUID sessionId = sessionOpt.get().getId();
        List<QueueEntry> entries;

        if ("ACTIVE".equalsIgnoreCase(statusFilter)) {
            entries = queueEntryRepository.findBySessionIdAndStatusInOrderByQueueNumberAsc(
                    sessionId, List.of(QueueStatus.WAITING, QueueStatus.ALMOST_READY, QueueStatus.CURRENT));
        } else {
            entries = queueEntryRepository.findBySessionIdOrderByQueueNumberAsc(sessionId);
        }

        return entries.stream().map(this::toEntryDto).toList();
    }

    private QueueSessionDto toSessionDto(QueueSession session) {
        QueueSessionDto dto = new QueueSessionDto();
        dto.setId(session.getId());
        dto.setSessionDate(session.getSessionDate());
        dto.setStatus(session.getStatus());
        dto.setLastQueueNumber(session.getLastQueueNumber());
        dto.setOpenedAt(session.getOpenedAt());
        dto.setClosedAt(session.getClosedAt());
        return dto;
    }

    private QueueEntryDto toEntryDto(QueueEntry entry) {
        QueueEntryDto dto = new QueueEntryDto();
        dto.setId(entry.getId());
        dto.setQueueNumber(entry.getQueueNumber());
        dto.setStudentName(entry.getStudentName());
        dto.setStudentId(entry.getStudentId());
        dto.setStatus(entry.getStatus());
        dto.setJoinedAt(entry.getJoinedAt());
        dto.setCalledAt(entry.getCalledAt());
        dto.setCompletedAt(entry.getCompletedAt());
        return dto;
    }
}
