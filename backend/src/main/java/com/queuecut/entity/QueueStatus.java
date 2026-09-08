package com.queuecut.entity;

/**
 * Represents the possible states a queue entry can be in.
 * Terminal states: COMPLETED, SKIPPED, CANCELLED
 * Active states: WAITING, ALMOST_READY, CURRENT
 */
public enum QueueStatus {
    WAITING,
    ALMOST_READY,
    CURRENT,
    COMPLETED,
    SKIPPED,
    CANCELLED;

    public boolean isActive() {
        return this == WAITING || this == ALMOST_READY || this == CURRENT;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }
}
