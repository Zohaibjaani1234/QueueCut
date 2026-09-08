package com.queuecut.dto.queue;

import com.queuecut.entity.SessionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QueueSessionDto {

    private UUID id;
    private LocalDate sessionDate;
    private SessionStatus status;
    private Integer lastQueueNumber;
    private Instant openedAt;
    private Instant closedAt;

    public QueueSessionDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Integer getLastQueueNumber() {
        return lastQueueNumber;
    }

    public void setLastQueueNumber(Integer lastQueueNumber) {
        this.lastQueueNumber = lastQueueNumber;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
