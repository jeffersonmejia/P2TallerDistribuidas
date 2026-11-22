package edu.restaurant.features.demonstrations.application;

public interface DemoEvidencePort {
    DemoSeedData seedData();

    DemoDatabaseSnapshot snapshot();

    DemoOrderEvidence order(int orderId);
}
