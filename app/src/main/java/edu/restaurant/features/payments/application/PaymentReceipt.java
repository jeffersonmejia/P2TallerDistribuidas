package edu.restaurant.features.payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReceipt(int id, int orderId, BigDecimal amount, LocalDate paymentDate, String method) {
}
