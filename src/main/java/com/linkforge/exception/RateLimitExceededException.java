package com.linkforge.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends LinkForgeException {
    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
