package edu.restaurant.shared.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiErrorTest {
    @Test
    void carriesStableSafeFields() {
        ApiError error = new ApiError(Instant.parse("2026-01-01T00:00:00Z"), 500, "INTERNAL_ERROR", "Ocurrió un error interno.", "trace-1");
        assertThat(error.message()).doesNotContain("SQL", "Exception", "stack");
        assertThat(error.correlationId()).isEqualTo("trace-1");
    }
}

