package edu.restaurant.features.demonstrations.application;

import java.math.BigDecimal;

public record DemoOrderEvidence(int orderId, boolean exists, String status, long itemCount, long quantity,
                                long paymentCount, BigDecimal paymentAmount, long paymentAttemptCount,
                                long completedEventCount, String lastAttemptActor, String lastAttemptOperationKey,
                                String lastAttemptCorrelationId, String lastCompletedCorrelationId) {
}
