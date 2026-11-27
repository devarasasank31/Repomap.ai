package com.repomap.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Stable, machine readable error payload returned by the global exception
 * handler and by the security layer. Never contains server filesystem paths or
 * internal stack traces.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, Map.of());
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> details) {
        return new ApiError(Instant.now(), status, error, message, path, details);
    }
}
