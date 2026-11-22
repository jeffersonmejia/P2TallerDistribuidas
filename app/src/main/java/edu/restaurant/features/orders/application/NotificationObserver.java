package edu.restaurant.features.orders.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationObserver {
    private static final Logger logger = LoggerFactory.getLogger(NotificationObserver.class);
    private final NotificationPort notifications;

    public NotificationObserver(NotificationPort notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        try {
            notifications.notifyOrderCompleted(event);
        } catch (RuntimeException exception) {
            logger.error("No se pudo notificar la orden completada orderId={} correlationId={}",
                    event.orderId(), event.correlationId(), exception);
        }
    }
}
