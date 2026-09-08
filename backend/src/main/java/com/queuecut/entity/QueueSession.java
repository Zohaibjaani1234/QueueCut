package com.queuecut.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single day's queue session.
 * A new session is created each day when the barber opens the queue.
 * last_queue_number is the atomic counter for sequential queue number generation.
 */
@Entity
@Table(name = "queue_session",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"barber_id", "session_date"})
        })
public class QueueSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private BarberAccount barber;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.OPEN;

    /**
     * Atomic counter for queue number generation.
     * Incremented via UPDATE ... RETURNING in a transaction — never read-then-write.
     */
    @Column(name = "last_queue_number", nullable = false)
    private Integer lastQueueNumber = 0;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("queueNumber ASC")
    private List<QueueEntry> entries = new ArrayList<>();

    public QueueSession() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (openedAt == null) openedAt = now;
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

    public BarberAccount getBarber() {
        return barber;
    }

    public void setBarber(BarberAccount barber) {
        this.barber = barber;
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

    public List<QueueEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<QueueEntry> entries) {
        this.entries = entries;
    }
}
