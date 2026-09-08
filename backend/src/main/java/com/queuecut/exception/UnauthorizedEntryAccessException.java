package com.queuecut.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedEntryAccessException extends RuntimeException {
    public UnauthorizedEntryAccessException() {
        super("You are not authorized to access or modify this queue entry.");
    }
}
