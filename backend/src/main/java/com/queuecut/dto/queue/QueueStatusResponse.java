package com.queuecut.dto.queue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class QueueStatusResponse {

    private boolean queueOpen;
    private UUID sessionId;
    private LocalDate sessionDate;
    private Integer currentTicket;
    private String currentStudentName;
    private long totalWaiting;
    private int estimatedWaitMinutes;
    private int avgHaircutMinutes;
    private Instant lastCalledAt;

    public QueueStatusResponse() {
    }

    public boolean isQueueOpen() {
        return queueOpen;
    }

    public void setQueueOpen(boolean queueOpen) {
        this.queueOpen = queueOpen;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public Integer getCurrentTicket() {
        return currentTicket;
    }

    public void setCurrentTicket(Integer currentTicket) {
        this.currentTicket = currentTicket;
    }

    public String getCurrentStudentName() {
        return currentStudentName;
    }

    public void setCurrentStudentName(String currentStudentName) {
        this.currentStudentName = currentStudentName;
    }

    public long getTotalWaiting() {
        return totalWaiting;
    }

    public void setTotalWaiting(long totalWaiting) {
        this.totalWaiting = totalWaiting;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    public int getAvgHaircutMinutes() {
        return avgHaircutMinutes;
    }

    public void setAvgHaircutMinutes(int avgHaircutMinutes) {
        this.avgHaircutMinutes = avgHaircutMinutes;
    }

    public Instant getLastCalledAt() {
        return lastCalledAt;
    }

    public void setLastCalledAt(Instant lastCalledAt) {
        this.lastCalledAt = lastCalledAt;
    }
}
