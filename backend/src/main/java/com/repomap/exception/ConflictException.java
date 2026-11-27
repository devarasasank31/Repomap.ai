package com.repomap.exception;

/**
 * Thrown when a resource already exists (duplicate username, duplicate import
 * key, ...). Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
