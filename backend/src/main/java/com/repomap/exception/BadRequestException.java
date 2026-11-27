package com.repomap.exception;

/**
 * Thrown when the caller submits a request that is syntactically valid but
 * semantically unacceptable (unsupported file type, invalid state transition,
 * ...). Maps to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
