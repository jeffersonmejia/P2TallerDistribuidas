package edu.restaurant.features.orders.infrastructure.notification;

import edu.restaurant.features.orders.application.NotificationAcknowledgement;
import edu.restaurant.features.orders.application.NotificationPort;
import edu.restaurant.features.orders.application.OrderCompletedEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ConsoleNotificationAdapter implements NotificationPort {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleNotificationAdapter.class);

    @Override
    public NotificationAcknowledgement notifyOrderCompleted(OrderCompletedEvent event) {
        logger.info("Notificación de orden completada orderId={} paymentId={} correlationId={}",
                event.orderId(), event.paymentId(), event.correlationId());
        return new NotificationAcknowledgement(true, "console", event.orderId(), Instant.now());
    }
}
