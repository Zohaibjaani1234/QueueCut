package com.queuecut.service;

import com.queuecut.dto.queue.JoinQueueRequest;
import com.queuecut.dto.queue.JoinQueueResponse;
import com.queuecut.dto.queue.MyStatusResponse;
import com.queuecut.dto.queue.QueueStatusResponse;
import com.queuecut.entity.QueueEntry;
import com.queuecut.entity.QueueSession;
import com.queuecut.entity.QueueStatus;
import com.queuecut.entity.SessionStatus;
import com.queuecut.exception.AlreadyInQueueException;
import com.queuecut.exception.EntryNotFoundException;
import com.queuecut.exception.InvalidStateTransitionException;
import com.queuecut.exception.QueueClosedException;
import com.queuecut.exception.UnauthorizedEntryAccessException;
import com.queuecut.repository.QueueEntryRepository;
import com.queuecut.repository.QueueSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueueService {

    private final QueueSessionRepository queueSessionRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final SettingsService settingsService;
    private final SseService sseService;

    public QueueService(QueueSessionRepository queueSessionRepository,
                        QueueEntryRepository queueEntryRepository,
                        SettingsService settingsService,
                        SseService sseService) {
        this.queueSessionRepository = queueSessionRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.settingsService = settingsService;
        this.sseService = sseService;
    }

    /**
     * Get the public status of the queue for today.
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getPublicStatus() {
        LocalDate today = LocalDate.now();
        Optional<QueueSession> sessionOpt = queueSessionRepository.findBySessionDate(today);

        QueueStatusResponse response = new QueueStatusResponse();
        int avgHaircutMin = settingsService.getAvgHaircutMinutes();
        response.setAvgHaircutMinutes(avgHaircutMin);
        response.setSessionDate(today);

        if (sessionOpt.isEmpty() || sessionOpt.get().getStatus() != SessionStatus.OPEN) {
            response.setQueueOpen(false);
            if (sessionOpt.isPresent()) {
                response.setSessionId(sessionOpt.get().getId());
            }
            response.setTotalWaiting(0);
            response.setEstimatedWaitMinutes(0);
            return response;
        }

        QueueSession session = sessionOpt.get();
        response.setQueueOpen(true);
        response.setSessionId(session.getId());

        // Find student currently in the chair
        Optional<QueueEntry> currentEntryOpt = queueEntryRepository.findBySessionIdAndStatus(session.getId(), QueueStatus.CURRENT);
        if (currentEntryOpt.isPresent()) {
            QueueEntry current = currentEntryOpt.get();
            response.setCurrentTicket(current.getQueueNumber());
            response.setCurrentStudentName(current.getStudentName());
            response.setLastCalledAt(current.getCalledAt());
        }

        long activeEntries = queueEntryRepository.countActiveEntries(session.getId());
        long waitingCount = currentEntryOpt.isPresent() ? Math.max(0, activeEntries - 1) : activeEntries;
        response.setTotalWaiting(waitingCount);
        // Wait for someone joining now: everyone in the queue (including the chair) is ahead of them
        response.setEstimatedWaitMinutes((int) (activeEntries * avgHaircutMin));

        return response;
    }

    /**
     * Join the queue for today's open session.
     */
    @Transactional
    public JoinQueueResponse joinQueue(JoinQueueRequest request) {
        LocalDate today = LocalDate.now();
        QueueSession session = queueSessionRepository.findBySessionDateAndStatus(today, SessionStatus.OPEN)
                .orElseThrow(QueueClosedException::new);

        // Upper-case so 24f-3089 and 24F-3089 are the same student (one active ticket each)
        String trimmedStudentId = request.getStudentId().trim().toUpperCase(java.util.Locale.ROOT);
        String trimmedName = request.getStudentName().trim();

        // Serialize joins for this session so the duplicate check below can't race
        queueSessionRepository.lockById(session.getId());

        // Check if student already has an active entry in this session
        if (queueEntryRepository.existsActiveEntryForStudent(session.getId(), trimmedStudentId)) {
            throw new AlreadyInQueueException();
        }

        // Counts total joins for the day; returns 0 if the session was closed meanwhile
        int updatedRows = queueSessionRepository.incrementQueueNumber(session.getId());
        if (updatedRows == 0) {
            throw new QueueClosedException();
        }

        // Ticket = students in the queue + 1 (#1 when empty). Numbers are kept as positions 1..N,
        // so compacting first also repairs any older non-contiguous numbering.
        // Safe under concurrency: joins for this session are serialized by the lock above.
        queueEntryRepository.compactActiveNumbers(session.getId());
        int newQueueNumber = queueEntryRepository.findMaxActiveQueueNumber(session.getId()) + 1;

        QueueEntry entry = new QueueEntry();
        entry.setSession(session);
        entry.setQueueNumber(newQueueNumber);
        entry.setStudentName(trimmedName);
        entry.setStudentId(trimmedStudentId);
        entry.setStatus(QueueStatus.WAITING);

        QueueEntry savedEntry = queueEntryRepository.save(entry);

        // Check if this student is the first one waiting (and chair is occupied), promote to ALMOST_READY
        promoteCandidateIfNeeded(session.getId());

        long peopleAhead = queueEntryRepository.countPeopleAhead(session.getId(), savedEntry.getQueueNumber());
        int avgHaircutMin = settingsService.getAvgHaircutMinutes();
        int estimatedWait = (int) (peopleAhead * avgHaircutMin);

        // Broadcast real-time SSE event to all connected clients
        sseService.broadcast("QUEUE_UPDATED", getPublicStatus());

        JoinQueueResponse response = new JoinQueueResponse();
        response.setEntryId(savedEntry.getId());
        response.setSessionId(session.getId());
        response.setQueueNumber(savedEntry.getQueueNumber());
        response.setStudentName(savedEntry.getStudentName());
        response.setStudentId(savedEntry.getStudentId());
        response.setStudentToken(savedEntry.getStudentToken());
        response.setStatus(savedEntry.getStatus());
        response.setPeopleAhead(peopleAhead);
        response.setEstimatedWaitMinutes(estimatedWait);
        response.setJoinedAt(savedEntry.getJoinedAt());

        return response;
    }

    /**
     * Get private ticket status for an individual student using their entryId and secret studentToken.
     */
    @Transactional(readOnly = true)
    public MyStatusResponse getMyStatus(UUID entryId, UUID studentToken) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(EntryNotFoundException::new);

        if (studentToken == null || !studentToken.equals(entry.getStudentToken())) {
            throw new UnauthorizedEntryAccessException();
        }

        long peopleAhead = queueEntryRepository.countPeopleAhead(entry.getSession().getId(), entry.getQueueNumber());
        int avgHaircutMin = settingsService.getAvgHaircutMinutes();
        int estimatedWait = (int) (peopleAhead * avgHaircutMin);
        boolean isOpen = entry.getSession().getStatus() == SessionStatus.OPEN;

        MyStatusResponse response = new MyStatusResponse();
        response.setEntryId(entry.getId());
        response.setQueueNumber(entry.getQueueNumber());
        response.setStudentName(entry.getStudentName());
        response.setStudentId(entry.getStudentId());
        response.setStatus(entry.getStatus());
        response.setPeopleAhead(peopleAhead);
        response.setEstimatedWaitMinutes(estimatedWait);
        response.setQueueOpen(isOpen);
        response.setCalledAt(entry.getCalledAt());

        return response;
    }

    /**
     * Student cancels their own queue entry using their secret studentToken.
     */
    @Transactional
    public void cancelEntry(UUID entryId, UUID studentToken) {
        queueEntryRepository.findSessionIdByEntryId(entryId).ifPresent(queueSessionRepository::lockById);
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(EntryNotFoundException::new);

        if (studentToken == null || !studentToken.equals(entry.getStudentToken())) {
            throw new UnauthorizedEntryAccessException();
        }

        if (entry.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException(entry.getStatus(), QueueStatus.CANCELLED);
        }

        if (entry.getStatus() == QueueStatus.CURRENT) {
            throw new InvalidStateTransitionException(QueueStatus.CURRENT, QueueStatus.CANCELLED);
        }

        UUID sessionId = entry.getSession().getId();
        entry.setStatus(QueueStatus.CANCELLED);
        queueEntryRepository.save(entry);
        queueEntryRepository.compactActiveNumbers(sessionId); // everyone behind moves up one

        // Re-evaluate if someone else should now be ALMOST_READY
        promoteCandidateIfNeeded(sessionId);

        sseService.broadcast("QUEUE_UPDATED", getPublicStatus());
    }

    /**
     * Checks waiting entries and sets the next eligible student to ALMOST_READY if someone is CURRENT in the chair.
     */
    public void promoteCandidateIfNeeded(UUID sessionId) {
        Optional<QueueEntry> currentInChair = queueEntryRepository.findBySessionIdAndStatus(sessionId, QueueStatus.CURRENT);
        if (currentInChair.isPresent()) {
            Optional<QueueEntry> nextWaiting = queueEntryRepository.findNextWaitingEntry(sessionId);
            if (nextWaiting.isPresent() && nextWaiting.get().getStatus() == QueueStatus.WAITING) {
                QueueEntry toPromote = nextWaiting.get();
                toPromote.setStatus(QueueStatus.ALMOST_READY);
                queueEntryRepository.save(toPromote);
            }
        }
    }
}
