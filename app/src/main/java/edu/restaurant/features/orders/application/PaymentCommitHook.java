package edu.restaurant.features.orders.application;

public interface PaymentCommitHook {
    void afterWrites(boolean injectFailure);
}
