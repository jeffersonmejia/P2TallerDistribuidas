package edu.restaurant.features.demonstrations.application;

public record DemoLabStatus(boolean enabled, DemoSimulatorState simulator,
                            DemoBreakerState circuitBreaker, DemoDatabaseSnapshot database) {
}
