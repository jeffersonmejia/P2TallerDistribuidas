package edu.restaurant.features.payments.application;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentAuthorization(boolean authorized, BigDecimal amount, String reference, Instant authorizedAt) {
}
