package com.repomap.exception;

/**
 * Thrown when credentials are missing or wrong. Maps to HTTP 401 without
 * revealing whether the account exists.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
