package edu.restaurant.features.audit.presentation;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditEventResponse(long id, String action, String result, Integer orderId, Integer paymentId,
                                 BigDecimal amount, String actor, Instant occurredAt,
                                 String correlationId, String operationKey) {
}
