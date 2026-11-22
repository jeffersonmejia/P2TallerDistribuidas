package edu.restaurant.features.payments.presentation;

public record CircuitBreakerSnapshot(String state, long bufferedCalls, long successfulCalls,
                                     long failedCalls, long notPermittedCalls, Double failureRatePercent) {
}
