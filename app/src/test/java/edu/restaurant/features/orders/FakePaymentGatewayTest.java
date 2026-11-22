package edu.restaurant.features.orders;

import static org.assertj.core.api.Assertions.assertThat;

import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.infrastructure.gateway.FakePaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {
    @Test
    void repeatsTheSameAuthorizationForAnIdempotencyKey() {
        FakePaymentGateway gateway = new FakePaymentGateway();
        BigDecimal amount = new BigDecimal("25.98");

        PaymentAuthorization first = gateway.authorize(42, amount, "operation-42");
        PaymentAuthorization retry = gateway.authorize(42, amount, "operation-42");

        assertThat(first.authorized()).isTrue();
        assertThat(retry).isEqualTo(first);
    }

    @Test
    void doesNotReuseAnIdempotencyKeyForAnotherOrderOrAmount() {
        FakePaymentGateway gateway = new FakePaymentGateway();
        gateway.authorize(42, new BigDecimal("25.98"), "operation-42");

        PaymentAuthorization mismatched = gateway.authorize(43, new BigDecimal("25.98"), "operation-42");

        assertThat(mismatched.authorized()).isFalse();
    }
}
