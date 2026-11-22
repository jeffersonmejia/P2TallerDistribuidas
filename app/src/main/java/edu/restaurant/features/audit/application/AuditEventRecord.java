package edu.restaurant.features.audit.application;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditEventRecord(String action, String result, Integer orderId, Integer paymentId,
                               BigDecimal amount, String actor, Instant occurredAt,
                               String correlationId, String operationKey) {
}
