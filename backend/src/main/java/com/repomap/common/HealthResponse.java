package com.repomap.common;

import java.time.Instant;

public record HealthResponse(String status, String service, Instant timestamp) {
}
