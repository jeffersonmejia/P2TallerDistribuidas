package edu.restaurant.features.payments.application;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentAuthorization authorize(int orderId, BigDecimal amount, String idempotencyKey);
}
