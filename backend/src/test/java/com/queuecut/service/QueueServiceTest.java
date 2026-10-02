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
import com.queuecut.exception.InvalidStateTransitionException;
import com.queuecut.exception.QueueClosedException;
import com.queuecut.exception.UnauthorizedEntryAccessException;
import com.queuecut.repository.QueueEntryRepository;
import com.queuecut.repository.QueueSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private QueueService queueService;

    private QueueSession mockSession;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        mockSession = new QueueSession();
        mockSession.setId(sessionId);
        mockSession.setSessionDate(LocalDate.now());
        mockSession.setStatus(SessionStatus.OPEN);
        mockSession.setLastQueueNumber(5);
    }

    @Test
    @DisplayName("joinQueue: successfully joins when session is OPEN")
    void joinQueue_Success() {
        when(queueSessionRepository.findBySessionDateAndStatus(eq(LocalDate.now()), eq(SessionStatus.OPEN)))
                .thenReturn(Optional.of(mockSession));
        when(queueEntryRepository.existsActiveEntryForStudent(sessionId, "21K-3890"))
                .thenReturn(false);
        when(queueSessionRepository.incrementQueueNumber(sessionId)).thenReturn(1);
        when(queueEntryRepository.findMaxActiveQueueNumber(sessionId)).thenReturn(5);
        when(settingsService.getAvgHaircutMinutes()).thenReturn(20);
        when(queueEntryRepository.countPeopleAhead(sessionId, 6)).thenReturn(2L);

        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(invocation -> {
            QueueEntry entry = invocation.getArgument(0);
            entry.setId(UUID.randomUUID());
            entry.setStudentToken(UUID.randomUUID());
            return entry;
        });

        JoinQueueRequest request = new JoinQueueRequest("Usman Tariq", "21K-3890");
        JoinQueueResponse response = queueService.joinQueue(request);

        assertThat(response).isNotNull();
        assertThat(response.getQueueNumber()).isEqualTo(6);
        assertThat(response.getStudentName()).isEqualTo("Usman Tariq");
        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.getPeopleAhead()).isEqualTo(2L);
        assertThat(response.getEstimatedWaitMinutes()).isEqualTo(40);
        verify(sseService).broadcast(eq("QUEUE_UPDATED"), any());
    }

    @Test
    @DisplayName("joinQueue: throws QueueClosedException when queue is closed")
    void joinQueue_QueueClosed() {
        when(queueSessionRepository.findBySessionDateAndStatus(eq(LocalDate.now()), eq(SessionStatus.OPEN)))
                .thenReturn(Optional.empty());

        JoinQueueRequest request = new JoinQueueRequest("Usman", "21K-3890");

        assertThatThrownBy(() -> queueService.joinQueue(request))
                .isInstanceOf(QueueClosedException.class);
    }

    @Test
    @DisplayName("joinQueue: throws AlreadyInQueueException when student is already active")
    void joinQueue_AlreadyInQueue() {
        when(queueSessionRepository.findBySessionDateAndStatus(eq(LocalDate.now()), eq(SessionStatus.OPEN)))
                .thenReturn(Optional.of(mockSession));
        when(queueEntryRepository.existsActiveEntryForStudent(sessionId, "21K-3890"))
                .thenReturn(true);

        JoinQueueRequest request = new JoinQueueRequest("Usman", "21K-3890");

        assertThatThrownBy(() -> queueService.joinQueue(request))
                .isInstanceOf(AlreadyInQueueException.class);
    }

    @Test
    @DisplayName("getMyStatus: returns status when studentToken matches")
    void getMyStatus_Success() {
        UUID entryId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        QueueEntry entry = new QueueEntry();
        entry.setId(entryId);
        entry.setSession(mockSession);
        entry.setQueueNumber(3);
        entry.setStudentName("Ali Khan");
        entry.setStudentId("20K-1122");
        entry.setStudentToken(token);
        entry.setStatus(QueueStatus.WAITING);

        when(queueEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(queueEntryRepository.countPeopleAhead(sessionId, 3)).thenReturn(1L);
        when(settingsService.getAvgHaircutMinutes()).thenReturn(20);

        MyStatusResponse response = queueService.getMyStatus(entryId, token);

        assertThat(response.getQueueNumber()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.getPeopleAhead()).isEqualTo(1L);
        assertThat(response.getEstimatedWaitMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("getMyStatus: throws UnauthorizedEntryAccessException when token is mismatched")
    void getMyStatus_InvalidToken() {
        UUID entryId = UUID.randomUUID();
        UUID correctToken = UUID.randomUUID();
        UUID wrongToken = UUID.randomUUID();

        QueueEntry entry = new QueueEntry();
        entry.setId(entryId);
        entry.setSession(mockSession);
        entry.setStudentToken(correctToken);

        when(queueEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> queueService.getMyStatus(entryId, wrongToken))
                .isInstanceOf(UnauthorizedEntryAccessException.class);
    }

    @Test
    @DisplayName("cancelEntry: cancels active entry successfully")
    void cancelEntry_Success() {
        UUID entryId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        QueueEntry entry = new QueueEntry();
        entry.setId(entryId);
        entry.setSession(mockSession);
        entry.setStudentToken(token);
        entry.setStatus(QueueStatus.WAITING);

        when(queueEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        queueService.cancelEntry(entryId, token);

        assertThat(entry.getStatus()).isEqualTo(QueueStatus.CANCELLED);
        verify(queueEntryRepository).save(entry);
        verify(queueEntryRepository).compactActiveNumbers(sessionId); // people behind move up
        verify(sseService).broadcast(eq("QUEUE_UPDATED"), any());
    }

    @Test
    @DisplayName("cancelEntry: throws exception if trying to cancel CURRENT entry")
    void cancelEntry_CurrentEntry() {
        UUID entryId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        QueueEntry entry = new QueueEntry();
        entry.setId(entryId);
        entry.setSession(mockSession);
        entry.setStudentToken(token);
        entry.setStatus(QueueStatus.CURRENT);

        when(queueEntryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> queueService.cancelEntry(entryId, token))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("joinQueue: first student in an empty queue gets ticket #1 with no wait")
    void joinQueue_EmptyQueueGetsNumberOne() {
        when(queueSessionRepository.findBySessionDateAndStatus(eq(LocalDate.now()), eq(SessionStatus.OPEN)))
                .thenReturn(Optional.of(mockSession));
        when(queueSessionRepository.incrementQueueNumber(sessionId)).thenReturn(1);
        when(queueEntryRepository.findMaxActiveQueueNumber(sessionId)).thenReturn(0);
        when(queueEntryRepository.countPeopleAhead(sessionId, 1)).thenReturn(0L);
        when(settingsService.getAvgHaircutMinutes()).thenReturn(20);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(i -> i.getArgument(0));

        JoinQueueResponse response = queueService.joinQueue(new JoinQueueRequest("Usman Tariq", "21k-3890"));

        assertThat(response.getQueueNumber()).isEqualTo(1);
        assertThat(response.getStudentId()).isEqualTo("21K-3890");
        assertThat(response.getPeopleAhead()).isZero();
        assertThat(response.getEstimatedWaitMinutes()).isZero();
    }

    @Test
    @DisplayName("joinQueue: with 3 students ahead the wait is 3 x 20 = 60 minutes")
    void joinQueue_ThreeAheadWaitsSixtyMinutes() {
        when(queueSessionRepository.findBySessionDateAndStatus(eq(LocalDate.now()), eq(SessionStatus.OPEN)))
                .thenReturn(Optional.of(mockSession));
        when(queueSessionRepository.incrementQueueNumber(sessionId)).thenReturn(1);
        when(queueEntryRepository.findMaxActiveQueueNumber(sessionId)).thenReturn(3);
        when(queueEntryRepository.countPeopleAhead(sessionId, 4)).thenReturn(3L);
        when(settingsService.getAvgHaircutMinutes()).thenReturn(20);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(i -> i.getArgument(0));

        JoinQueueResponse response = queueService.joinQueue(new JoinQueueRequest("Sara Khan", "22F-1001"));

        assertThat(response.getQueueNumber()).isEqualTo(4);
        assertThat(response.getPeopleAhead()).isEqualTo(3L);
        assertThat(response.getEstimatedWaitMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("getPublicStatus: wait estimate counts the student in the chair too")
    void getPublicStatus_EstimateIncludesChair() {
        QueueEntry inChair = new QueueEntry();
        inChair.setQueueNumber(1);
        inChair.setStudentName("Ali");
        inChair.setStatus(QueueStatus.CURRENT);

        when(queueSessionRepository.findBySessionDate(any(LocalDate.class))).thenReturn(Optional.of(mockSession));
        when(settingsService.getAvgHaircutMinutes()).thenReturn(20);
        when(queueEntryRepository.findBySessionIdAndStatus(sessionId, QueueStatus.CURRENT)).thenReturn(Optional.of(inChair));
        when(queueEntryRepository.countActiveEntries(sessionId)).thenReturn(3L); // 1 in chair + 2 waiting

        QueueStatusResponse status = queueService.getPublicStatus();

        assertThat(status.getTotalWaiting()).isEqualTo(2L);
        assertThat(status.getEstimatedWaitMinutes()).isEqualTo(60);
        assertThat(status.getCurrentTicket()).isEqualTo(1);
    }
}
