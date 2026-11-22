package edu.restaurant.features.payments.infrastructure.proxy;

import edu.restaurant.features.audit.application.AuditEventRecord;
import edu.restaurant.features.audit.application.AuditPort;
import edu.restaurant.features.orders.application.OrderApplicationService;
import edu.restaurant.features.orders.application.OrderPaymentResult;
import edu.restaurant.features.orders.application.PaymentOperation;
import edu.restaurant.features.orders.domain.OrderException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class PaymentServiceProxy implements PaymentOperation {
    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceProxy.class);
    private static final String ACTOR = "DEMO_OPERATOR";
    private final OrderApplicationService realService;
    private final AuditPort audit;

    public PaymentServiceProxy(OrderApplicationService realService, AuditPort audit) {
        this.realService = realService;
        this.audit = audit;
    }

    @Override
    public OrderPaymentResult confirmPayment(int orderId, String paymentMethod, String idempotencyKey, boolean injectFailure) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        Instant attemptedAt = Instant.now();
        logger.info("PaymentServiceProxy delegating orderId={} actor={} correlationId={}", orderId, ACTOR, correlationId);
        try {
            OrderPaymentResult result = realService.confirmPayment(orderId, paymentMethod, idempotencyKey, injectFailure);
            persistAttempt("CONFIRMED", orderId, result.paymentId(), result.amount(), idempotencyKey, attemptedAt, correlationId);
            return result;
        } catch (RuntimeException exception) {
            String outcome = exception instanceof OrderException ? "REJECTED" : "FAILED";
            persistAttempt(outcome, orderId, null, null, idempotencyKey, attemptedAt, correlationId);
            logger.info("PaymentServiceProxy completed with {} orderId={} correlationId={}", outcome, orderId, correlationId);
            throw exception;
        }
    }

    private void persistAttempt(String result, int orderId, Integer paymentId, java.math.BigDecimal amount,
                                String operationKey, Instant occurredAt, String correlationId) {
        try {
            audit.record(new AuditEventRecord("PAYMENT_ATTEMPT", result, orderId, paymentId, amount,
                    ACTOR, occurredAt, correlationId, operationKey));
        } catch (RuntimeException exception) {
            logger.error("No se pudo persistir el resultado auditado orderId={} correlationId={}", orderId, correlationId, exception);
        }
    }
}
