package com.queuecut.service;

import com.queuecut.dto.queue.CallNextResponse;
import com.queuecut.dto.queue.QueueEntryDto;
import com.queuecut.dto.queue.QueueSessionDto;
import com.queuecut.entity.BarberAccount;
import com.queuecut.entity.QueueEntry;
import com.queuecut.entity.QueueSession;
import com.queuecut.entity.QueueStatus;
import com.queuecut.entity.SessionStatus;
import com.queuecut.repository.BarberAccountRepository;
import com.queuecut.repository.QueueEntryRepository;
import com.queuecut.repository.QueueSessionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BarberServiceTest {

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private BarberAccountRepository barberAccountRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private BarberService barberService;

    @Test
    @DisplayName("openSession: creates and opens session for today")
    void openSession_Success() {
        BarberAccount barber = new BarberAccount();
        barber.setUsername("arslan");

        when(barberAccountRepository.findByUsername("arslan")).thenReturn(Optional.of(barber));
        when(queueSessionRepository.findBySessionDate(any(LocalDate.class))).thenReturn(Optional.empty());

        when(queueSessionRepository.save(any(QueueSession.class))).thenAnswer(invocation -> {
            QueueSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        QueueSessionDto sessionDto = barberService.openSession("arslan");

        assertThat(sessionDto).isNotNull();
        assertThat(sessionDto.getStatus()).isEqualTo(SessionStatus.OPEN);
        verify(sseService).broadcast(eq("SESSION_STATUS_CHANGED"), any());
    }

    @Test
    @DisplayName("callNext: marks current as COMPLETED and promotes next waiting to CURRENT")
    void callNext_Success() {
        UUID sessionId = UUID.randomUUID();
        QueueSession session = new QueueSession();
        session.setId(sessionId);
        session.setStatus(SessionStatus.OPEN);

        QueueEntry current = new QueueEntry();
        current.setId(UUID.randomUUID());
        current.setStatus(QueueStatus.CURRENT);

        QueueEntry nextWaiting = new QueueEntry();
        nextWaiting.setId(UUID.randomUUID());
        nextWaiting.setQueueNumber(10);
        nextWaiting.setStudentName("Hamza");
        nextWaiting.setStatus(QueueStatus.WAITING);

        when(queueSessionRepository.findBySessionDate(any(LocalDate.class))).thenReturn(Optional.of(session));
        when(queueEntryRepository.findBySessionIdAndStatus(sessionId, QueueStatus.CURRENT))
                .thenReturn(Optional.of(current));
        when(queueEntryRepository.findNextWaitingEntry(sessionId))
                .thenReturn(Optional.of(nextWaiting))
                .thenReturn(Optional.empty()); // no third person

        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(i -> i.getArgument(0));
        when(queueEntryRepository.countActiveEntries(sessionId)).thenReturn(1L);

        CallNextResponse response = barberService.callNext("arslan");

        assertThat(response).isNotNull();
        assertThat(current.getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(response.getCurrentEntry().getQueueNumber()).isEqualTo(10);
        assertThat(response.getCurrentEntry().getStatus()).isEqualTo(QueueStatus.CURRENT);
    }
}
