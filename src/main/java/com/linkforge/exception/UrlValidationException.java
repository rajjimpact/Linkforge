package com.linkforge.exception;

import org.springframework.http.HttpStatus;

public class UrlValidationException extends LinkForgeException {
    public UrlValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
