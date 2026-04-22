package com.leadflow.backend.exception;

/**
 * Exception thrown when attempting to register with an email that already exists.
 * Maps to HTTP 409 Conflict status.
 */
public class DuplicateEmailException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(String message) {
        super(message);
    }

    public DuplicateEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
