package edu.restaurant.features.orders.presentation;

import java.math.BigDecimal;

public record PaymentConfirmationResponse(int paymentId, int orderId, BigDecimal amount, String status) {
}
