package edu.restaurant.features.orders.application;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCompletedEvent(int orderId, int paymentId, BigDecimal amount,
                                  Instant occurredAt, String correlationId) {
}
