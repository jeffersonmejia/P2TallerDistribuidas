package edu.restaurant.features.demonstrations.application;

public record DemoSimulatorState(boolean available, int failuresRemaining, long callCount,
                                 long idempotentOperations) {
}
