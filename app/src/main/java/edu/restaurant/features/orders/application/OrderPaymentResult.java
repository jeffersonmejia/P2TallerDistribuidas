package edu.restaurant.features.orders.application;

import java.math.BigDecimal;

public record OrderPaymentResult(int paymentId, int orderId, BigDecimal amount, String status) {
}
