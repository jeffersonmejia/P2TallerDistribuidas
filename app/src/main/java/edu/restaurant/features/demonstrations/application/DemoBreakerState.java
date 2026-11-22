package edu.restaurant.features.demonstrations.application;

public record DemoBreakerState(String state, int bufferedCalls, long successfulCalls,
                               long failedCalls, long notPermittedCalls, Double failureRate) {
}
