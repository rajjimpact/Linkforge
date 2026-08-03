package com.linkforge.exception;

import org.springframework.http.HttpStatus;

public class DuplicateAliasException extends LinkForgeException {
    public DuplicateAliasException(String alias) {
        super("Custom alias '" + alias + "' is already taken. Please choose a different one.", HttpStatus.CONFLICT);
    }
}
