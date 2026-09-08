package com.queuecut.dto.queue;

import com.queuecut.entity.QueueStatus;

import java.time.Instant;
import java.util.UUID;

public class JoinQueueResponse {

    private UUID entryId;
    private UUID sessionId;
    private Integer queueNumber;
    private String studentName;
    private String studentId;
    private UUID studentToken;
    private QueueStatus status;
    private long peopleAhead;
    private int estimatedWaitMinutes;
    private Instant joinedAt;

    public JoinQueueResponse() {
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(Integer queueNumber) {
        this.queueNumber = queueNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public UUID getStudentToken() {
        return studentToken;
    }

    public void setStudentToken(UUID studentToken) {
        this.studentToken = studentToken;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
    }

    public long getPeopleAhead() {
        return peopleAhead;
    }

    public void setPeopleAhead(long peopleAhead) {
        this.peopleAhead = peopleAhead;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
