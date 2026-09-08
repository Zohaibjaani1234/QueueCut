package com.queuecut.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class QueueClosedException extends RuntimeException {
    public QueueClosedException() {
        super("The queue is currently closed. Please check back later.");
    }
}
