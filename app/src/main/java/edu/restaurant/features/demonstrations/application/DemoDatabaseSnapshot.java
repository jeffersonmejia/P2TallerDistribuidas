package edu.restaurant.features.demonstrations.application;

public record DemoDatabaseSnapshot(long orders, long orderItems, long payments,
                                   long paymentAttempts, long completedEvents, long laboratoryOrders) {
}
