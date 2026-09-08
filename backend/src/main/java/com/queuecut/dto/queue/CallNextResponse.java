package com.queuecut.dto.queue;

public class CallNextResponse {

    private QueueEntryDto currentEntry;
    private QueueEntryDto almostReadyEntry;
    private long totalWaiting;

    public CallNextResponse() {
    }

    public CallNextResponse(QueueEntryDto currentEntry, QueueEntryDto almostReadyEntry, long totalWaiting) {
        this.currentEntry = currentEntry;
        this.almostReadyEntry = almostReadyEntry;
        this.totalWaiting = totalWaiting;
    }

    public QueueEntryDto getCurrentEntry() {
        return currentEntry;
    }

    public void setCurrentEntry(QueueEntryDto currentEntry) {
        this.currentEntry = currentEntry;
    }

    public QueueEntryDto getAlmostReadyEntry() {
        return almostReadyEntry;
    }

    public void setAlmostReadyEntry(QueueEntryDto almostReadyEntry) {
        this.almostReadyEntry = almostReadyEntry;
    }

    public long getTotalWaiting() {
        return totalWaiting;
    }

    public void setTotalWaiting(long totalWaiting) {
        this.totalWaiting = totalWaiting;
    }
}
