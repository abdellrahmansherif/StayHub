package com.common;

import java.time.Instant;
// Defines a standardized structure for API error responses
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
}
