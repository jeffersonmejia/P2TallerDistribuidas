package edu.restaurant.features.payments.infrastructure.gateway;

import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.application.PaymentGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakePaymentGateway implements PaymentGateway {
    private final ConcurrentMap<String, RecordedAuthorization> operations = new ConcurrentHashMap<>();
    private final AtomicBoolean rejectNext = new AtomicBoolean();

    @Override
    public PaymentAuthorization authorize(int orderId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new PaymentAuthorization(false, amount, "", Instant.now());
        }
        RecordedAuthorization result = operations.computeIfAbsent(idempotencyKey, key -> {
            boolean authorized = !rejectNext.getAndSet(false);
            String reference = "SIM-" + Integer.toUnsignedString(key.hashCode(), 36).toUpperCase();
            return new RecordedAuthorization(orderId, amount, new PaymentAuthorization(authorized, amount, reference, Instant.now()));
        });
        if (result.orderId() != orderId || result.amount().compareTo(amount) != 0) {
            return new PaymentAuthorization(false, amount, result.authorization().reference(), result.authorization().authorizedAt());
        }
        return result.authorization();
    }

    public void rejectNextAuthorization() {
        rejectNext.set(true);
    }

    private record RecordedAuthorization(int orderId, BigDecimal amount, PaymentAuthorization authorization) {}
}
