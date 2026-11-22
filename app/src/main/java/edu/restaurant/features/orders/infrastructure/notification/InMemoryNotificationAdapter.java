package edu.restaurant.features.orders.infrastructure.notification;

import edu.restaurant.features.orders.application.NotificationAcknowledgement;
import edu.restaurant.features.orders.application.NotificationPort;
import edu.restaurant.features.orders.application.OrderCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryNotificationAdapter implements NotificationPort {
    private final List<OrderCompletedEvent> received = new CopyOnWriteArrayList<>();

    @Override
    public NotificationAcknowledgement notifyOrderCompleted(OrderCompletedEvent event) {
        received.add(event);
        return new NotificationAcknowledgement(true, "in-memory", event.orderId(), Instant.now());
    }

    public List<OrderCompletedEvent> received() {
        return List.copyOf(received);
    }

    public void clear() {
        received.clear();
    }
}
