package com.queuecut.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single student's position in a queue session.
 * This is the core entity — one row per student per session.
 * student_token is a lightweight opaque token for student auth (no login required).
 */
@Entity
@Table(name = "queue_entry")
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private QueueSession session;

    @Column(name = "queue_number", nullable = false)
    private Integer queueNumber;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "student_id", nullable = false, length = 50)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QueueStatus status = QueueStatus.WAITING;

    /**
     * Opaque UUID token returned to the student after joining.
     * Used to authenticate status queries and cancellation — no login required.
     */
    @Column(name = "student_token", nullable = false, unique = true, updatable = false)
    private UUID studentToken;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public QueueEntry() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (joinedAt == null) joinedAt = now;
        if (studentToken == null) studentToken = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QueueSession getSession() {
        return session;
    }

    public void setSession(QueueSession session) {
        this.session = session;
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

    public UUID getStudentToken() {
        return studentToken;
    }

    public void setStudentToken(UUID studentToken) {
        this.studentToken = studentToken;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the formatted queue number for display, e.g. "#05".
     */
    public String getFormattedQueueNumber() {
        return String.format("#%02d", queueNumber);
    }
}
