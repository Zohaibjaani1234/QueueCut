package com.queuecut.dto.queue;

import com.queuecut.entity.QueueStatus;

import java.time.Instant;
import java.util.UUID;

public class MyStatusResponse {

    private UUID entryId;
    private Integer queueNumber;
    private String studentName;
    private String studentId;
    private QueueStatus status;
    private long peopleAhead;
    private int estimatedWaitMinutes;
    private boolean queueOpen;
    private Instant calledAt;

    public MyStatusResponse() {
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
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

    public boolean isQueueOpen() {
        return queueOpen;
    }

    public void setQueueOpen(boolean queueOpen) {
        this.queueOpen = queueOpen;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }
}
