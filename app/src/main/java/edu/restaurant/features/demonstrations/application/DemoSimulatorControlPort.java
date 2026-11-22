package edu.restaurant.features.demonstrations.application;

public interface DemoSimulatorControlPort {
    DemoSimulatorState state();

    DemoSimulatorState injectFailures(int count);

    DemoSimulatorState setAvailable(boolean available);
}
