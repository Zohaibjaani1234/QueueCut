package com.queuecut.controller;

import com.queuecut.dto.queue.CallNextResponse;
import com.queuecut.dto.queue.QueueEntryDto;
import com.queuecut.dto.queue.QueueSessionDto;
import com.queuecut.entity.QueueStatus;
import com.queuecut.entity.SessionStatus;
import com.queuecut.exception.GlobalExceptionHandler;
import com.queuecut.service.BarberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BarberControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BarberService barberService;

    @InjectMocks
    private BarberController barberController;

    private UserDetails mockBarberUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(barberController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockBarberUser = new User("arslan", "password", Collections.emptyList());
    }

    @Test
    @DisplayName("GET /api/barber/session/current: returns current session")
    void getCurrentSession_Success() throws Exception {
        QueueSessionDto sessionDto = new QueueSessionDto();
        sessionDto.setId(UUID.randomUUID());
        sessionDto.setStatus(SessionStatus.OPEN);

        when(barberService.getCurrentSession()).thenReturn(sessionDto);

        mockMvc.perform(get("/api/barber/session/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST /api/barber/queue/{entryId}/complete: marks entry as completed")
    void completeEntry_Success() throws Exception {
        UUID entryId = UUID.randomUUID();
        QueueEntryDto entryDto = new QueueEntryDto();
        entryDto.setId(entryId);
        entryDto.setQueueNumber(8);
        entryDto.setStatus(QueueStatus.COMPLETED);

        when(barberService.completeEntry(eq(entryId))).thenReturn(entryDto);

        mockMvc.perform(post("/api/barber/queue/" + entryId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/barber/queue/{entryId}/skip: marks entry as skipped")
    void skipEntry_Success() throws Exception {
        UUID entryId = UUID.randomUUID();
        QueueEntryDto entryDto = new QueueEntryDto();
        entryDto.setId(entryId);
        entryDto.setQueueNumber(8);
        entryDto.setStatus(QueueStatus.SKIPPED);

        when(barberService.skipEntry(eq(entryId))).thenReturn(entryDto);

        mockMvc.perform(post("/api/barber/queue/" + entryId + "/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    @DisplayName("GET /api/barber/queue/entries: returns list of entries")
    void getEntries_Success() throws Exception {
        QueueEntryDto entryDto = new QueueEntryDto();
        entryDto.setQueueNumber(9);
        entryDto.setStudentName("Zain");

        when(barberService.getSessionEntries(eq("ACTIVE"))).thenReturn(List.of(entryDto));

        mockMvc.perform(get("/api/barber/queue/entries?status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueNumber").value(9))
                .andExpect(jsonPath("$[0].studentName").value("Zain"));
    }
}
