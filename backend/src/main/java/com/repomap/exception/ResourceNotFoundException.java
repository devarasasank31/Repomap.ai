package com.repomap.exception;

/**
 * Thrown when a requested aggregate (project, user, symbol, ...) does not exist
 * or is not visible to the caller. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
