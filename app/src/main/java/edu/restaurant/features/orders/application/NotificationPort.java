package edu.restaurant.features.orders.application;

public interface NotificationPort {
    NotificationAcknowledgement notifyOrderCompleted(OrderCompletedEvent event);
}
