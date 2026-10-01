package com.queuecut.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class QueueAlreadyAdvancedException extends RuntimeException {
    public QueueAlreadyAdvancedException() {
        super("The queue already moved on (another tap or device). Screen refreshed — check who is in the chair.");
    }
}
