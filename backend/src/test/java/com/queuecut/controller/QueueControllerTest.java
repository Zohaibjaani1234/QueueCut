package com.queuecut.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuecut.dto.queue.JoinQueueRequest;
import com.queuecut.dto.queue.JoinQueueResponse;
import com.queuecut.dto.queue.MyStatusResponse;
import com.queuecut.dto.queue.QueueStatusResponse;
import com.queuecut.entity.QueueStatus;
import com.queuecut.exception.GlobalExceptionHandler;
import com.queuecut.exception.QueueClosedException;
import com.queuecut.service.QueueService;
import com.queuecut.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class QueueControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QueueService queueService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private QueueController queueController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(queueController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/queue/status: returns public queue status")
    void getStatus_Success() throws Exception {
        QueueStatusResponse status = new QueueStatusResponse();
        status.setQueueOpen(true);
        status.setCurrentTicket(12);
        status.setTotalWaiting(3);
        status.setEstimatedWaitMinutes(60);

        when(queueService.getPublicStatus()).thenReturn(status);

        mockMvc.perform(get("/api/queue/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueOpen").value(true))
                .andExpect(jsonPath("$.currentTicket").value(12))
                .andExpect(jsonPath("$.totalWaiting").value(3))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(60));
    }

    @Test
    @DisplayName("POST /api/queue/join: joins queue with valid payload")
    void joinQueue_Success() throws Exception {
        JoinQueueRequest request = new JoinQueueRequest("Usman Tariq", "21K-3890");

        JoinQueueResponse response = new JoinQueueResponse();
        response.setQueueNumber(15);
        response.setStudentName("Usman Tariq");
        response.setStatus(QueueStatus.WAITING);
        response.setPeopleAhead(2);

        when(queueService.joinQueue(any(JoinQueueRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueNumber").value(15))
                .andExpect(jsonPath("$.studentName").value("Usman Tariq"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("POST /api/queue/join: returns 400 Bad Request when fields are missing")
    void joinQueue_ValidationError() throws Exception {
        JoinQueueRequest invalidRequest = new JoinQueueRequest("", "");

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/queue/join: handles QueueClosedException with 409 Conflict")
    void joinQueue_QueueClosed() throws Exception {
        JoinQueueRequest request = new JoinQueueRequest("Usman Tariq", "21K-3890");

        when(queueService.joinQueue(any(JoinQueueRequest.class)))
                .thenThrow(new QueueClosedException());

        mockMvc.perform(post("/api/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/queue/my-status/{entryId}: returns individual status with header token")
    void getMyStatus_Success() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        MyStatusResponse response = new MyStatusResponse();
        response.setEntryId(entryId);
        response.setQueueNumber(15);
        response.setStatus(QueueStatus.ALMOST_READY);
        response.setPeopleAhead(1);

        when(queueService.getMyStatus(eq(entryId), eq(token))).thenReturn(response);

        mockMvc.perform(get("/api/queue/my-status/" + entryId)
                        .header("X-Student-Token", token.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(15))
                .andExpect(jsonPath("$.status").value("ALMOST_READY"));
    }

    @Test
    @DisplayName("POST /api/queue/{entryId}/cancel: cancels queue position")
    void cancelEntry_Success() throws Exception {
        UUID entryId = UUID.randomUUID();
        UUID token = UUID.randomUUID();

        doNothing().when(queueService).cancelEntry(eq(entryId), eq(token));

        mockMvc.perform(post("/api/queue/" + entryId + "/cancel")
                        .header("X-Student-Token", token.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
