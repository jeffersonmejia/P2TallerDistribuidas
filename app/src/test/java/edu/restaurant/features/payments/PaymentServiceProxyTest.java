package edu.restaurant.features.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.restaurant.features.audit.application.AuditEventRecord;
import edu.restaurant.features.audit.application.AuditPort;
import edu.restaurant.features.orders.application.OrderApplicationService;
import edu.restaurant.features.orders.application.OrderPaymentResult;
import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.features.payments.infrastructure.proxy.PaymentServiceProxy;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;

class PaymentServiceProxyTest {
    private final OrderApplicationService realService = mock(OrderApplicationService.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final PaymentServiceProxy proxy = new PaymentServiceProxy(realService, audit);

    @AfterEach void clearCorrelation() { MDC.clear(); }

    @Test
    void delegatesOnceAndAuditsSuccessfulResultWithActorAndCorrelation() {
        MDC.put("correlationId", "proxy-correlation-1");
        OrderPaymentResult expected = new OrderPaymentResult(42, 81, new BigDecimal("25.98"), "completed");
        when(realService.confirmPayment(81, "credit", "operation-81", false)).thenReturn(expected);

        assertThat(proxy.confirmPayment(81, "credit", "operation-81", false)).isEqualTo(expected);

        verify(realService, times(1)).confirmPayment(81, "credit", "operation-81", false);
        ArgumentCaptor<AuditEventRecord> event = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(event.getValue().result()).isEqualTo("CONFIRMED");
        assertThat(event.getValue().actor()).isEqualTo("DEMO_OPERATOR");
        assertThat(event.getValue().correlationId()).isEqualTo("proxy-correlation-1");
        assertThat(event.getValue().operationKey()).isEqualTo("operation-81");
    }

    @Test
    void auditsARejectionAndPreservesTheServiceError() {
        OrderException rejected = OrderException.paymentUnavailable();
        when(realService.confirmPayment(81, "credit", "operation-81", false)).thenThrow(rejected);

        assertThatThrownBy(() -> proxy.confirmPayment(81, "credit", "operation-81", false)).isSameAs(rejected);

        verify(realService, times(1)).confirmPayment(81, "credit", "operation-81", false);
        ArgumentCaptor<AuditEventRecord> event = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().result()).isEqualTo("REJECTED");
        assertThat(event.getValue().actor()).isEqualTo("DEMO_OPERATOR");
    }

    @Test
    void auditStorageFailureDoesNotChangeSuccessfulPaymentResult() {
        OrderPaymentResult expected = new OrderPaymentResult(42, 81, new BigDecimal("25.98"), "completed");
        when(realService.confirmPayment(81, "credit", "operation-81", false)).thenReturn(expected);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());

        assertThat(proxy.confirmPayment(81, "credit", "operation-81", false)).isEqualTo(expected);
        verify(audit).record(any());
        verify(realService, times(1)).confirmPayment(81, "credit", "operation-81", false);
    }
}
