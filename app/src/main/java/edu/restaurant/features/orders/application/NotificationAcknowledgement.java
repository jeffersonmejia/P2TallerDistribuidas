package edu.restaurant.features.orders.application;

import java.time.Instant;

public record NotificationAcknowledgement(boolean accepted, String channel, int orderId, Instant notifiedAt) {
}
