package com.linkforge.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends LinkForgeException {
    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
