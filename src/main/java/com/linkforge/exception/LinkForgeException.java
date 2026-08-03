package com.linkforge.exception;

import org.springframework.http.HttpStatus;

public class LinkForgeException extends RuntimeException {
    private final HttpStatus status;

    public LinkForgeException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
