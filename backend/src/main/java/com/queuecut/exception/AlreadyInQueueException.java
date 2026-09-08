package com.queuecut.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AlreadyInQueueException extends RuntimeException {
    public AlreadyInQueueException() {
        super("You are already in the queue. You cannot join again while your entry is active.");
    }
}
