package edu.restaurant.features.audit.application;

import edu.restaurant.features.orders.application.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditObserver {
    private static final Logger logger = LoggerFactory.getLogger(AuditObserver.class);
    private final AuditPort audit;

    public AuditObserver(AuditPort audit) {
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        try {
            audit.record(new AuditEventRecord("ORDER_COMPLETED", "COMMITTED", event.orderId(), event.paymentId(),
                    event.amount(), "DEMO_OPERATOR", event.occurredAt(), event.correlationId(), null));
        } catch (RuntimeException exception) {
            logger.error("No se pudo guardar la auditoría post-commit orderId={} correlationId={}",
                    event.orderId(), event.correlationId(), exception);
        }
    }
}
