package edu.restaurant.shared.presentation;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId) {
}

