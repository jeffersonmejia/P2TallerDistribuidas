package edu.restaurant.features.orders.application;

import edu.restaurant.features.catalog.application.MenuItemData;
import edu.restaurant.features.catalog.application.MenuQueryPort;
import edu.restaurant.features.orders.domain.OrderAggregate;
import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.features.orders.domain.OrderStatus;
import edu.restaurant.features.payments.application.PaymentPersistencePort;
import edu.restaurant.features.payments.application.PaymentReceipt;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalizeOrderPayment {
    private final OrderPersistencePort orders;
    private final PaymentPersistencePort payments;
    private final MenuQueryPort menu;
    private final PaymentCommitHook commitHook;
    private final ApplicationEventPublisher events;

    public FinalizeOrderPayment(OrderPersistencePort orders, PaymentPersistencePort payments, MenuQueryPort menu,
                                PaymentCommitHook commitHook, ApplicationEventPublisher events) {
        this.orders = orders;
        this.payments = payments;
        this.menu = menu;
        this.commitHook = commitHook;
        this.events = events;
    }

    @Transactional
    public OrderPaymentResult finish(int orderId, String method, BigDecimal authorizedAmount, boolean injectFailure) {
        OrderAggregate order = orders.findForUpdate(orderId)
                .orElseThrow(() -> OrderException.notFound("No se encontró la orden solicitada."));
        if (payments.findByOrderId(orderId).isPresent()) throw OrderException.paymentAlreadyConfirmed();
        order.ensureCanPay();
        Map<Integer, MenuItemData> products = menu.findByIds(order.items().stream().map(item -> item.menuItemId()).collect(java.util.stream.Collectors.toSet()));
        BigDecimal currentAmount = order.items().stream().map(item -> products.get(item.menuItemId()).price()
                .multiply(BigDecimal.valueOf(item.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (currentAmount.compareTo(authorizedAmount) != 0) {
            throw OrderException.conflict("La orden cambió durante la autorización. Vuelve a revisar el total e inténtalo de nuevo.");
        }
        try {
            PaymentReceipt receipt = payments.save(orderId, currentAmount, LocalDate.now(), method);
            orders.changeStatus(orderId, orders.statusId(OrderStatus.COMPLETED));
            commitHook.afterWrites(injectFailure);
            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isBlank()) correlationId = java.util.UUID.randomUUID().toString();
            events.publishEvent(new OrderCompletedEvent(orderId, receipt.id(), receipt.amount(), java.time.Instant.now(), correlationId));
            return new OrderPaymentResult(receipt.id(), orderId, receipt.amount(), OrderStatus.COMPLETED.databaseValue());
        } catch (DataIntegrityViolationException exception) {
            throw OrderException.paymentAlreadyConfirmed();
        }
    }
}
