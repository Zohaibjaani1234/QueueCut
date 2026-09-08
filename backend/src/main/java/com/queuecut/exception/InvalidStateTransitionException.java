package com.queuecut.exception;

import com.queuecut.entity.QueueStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(QueueStatus from, QueueStatus to) {
        super(String.format("Invalid state transition: cannot move from %s to %s.", from, to));
    }
}
