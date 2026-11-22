package edu.restaurant.features.orders.application;

import edu.restaurant.features.catalog.application.MenuItemData;
import edu.restaurant.features.catalog.application.MenuQueryPort;
import edu.restaurant.features.customers.application.CustomerQueryPort;
import edu.restaurant.features.orders.domain.OrderAggregate;
import edu.restaurant.features.orders.domain.OrderException;
import edu.restaurant.features.orders.domain.OrderItem;
import edu.restaurant.features.orders.domain.OrderStatus;
import edu.restaurant.features.orders.domain.OrderType;
import edu.restaurant.features.payments.application.PaymentAuthorization;
import edu.restaurant.features.payments.application.PaymentGateway;
import edu.restaurant.features.payments.application.PaymentPersistencePort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("9999.99");
    private static final Set<String> PAYMENT_METHODS = Set.of("cash", "credit", "debit", "dish-washing");

    private final OrderReadPort orderRead;
    private final OrderPersistencePort orderPersistence;
    private final CustomerQueryPort customers;
    private final MenuQueryPort menu;
    private final PaymentGateway gateway;
    private final PaymentPersistencePort payments;
    private final FinalizeOrderPayment finalizer;

    public OrderApplicationService(OrderReadPort orderRead, OrderPersistencePort orderPersistence,
                                   CustomerQueryPort customers, MenuQueryPort menu, PaymentGateway gateway,
                                   PaymentPersistencePort payments, FinalizeOrderPayment finalizer) {
        this.orderRead = orderRead;
        this.orderPersistence = orderPersistence;
        this.customers = customers;
        this.menu = menu;
        this.gateway = gateway;
        this.payments = payments;
        this.finalizer = finalizer;
    }

    public OrderPage list(String search, int page, int size) {
        if (page < 0 || page > 100_000) throw OrderException.validation("La página solicitada no es válida.");
        if (size < 1 || size > 100) throw OrderException.validation("El tamaño de página debe estar entre 1 y 100.");
        if (search != null && search.length() > 100) throw OrderException.validation("El texto de búsqueda no puede superar 100 caracteres.");
        return orderRead.search(search, page, size);
    }

    public OrderDetail get(int orderId) {
        return orderRead.detail(orderId).orElseThrow(() -> OrderException.notFound("No se encontró la orden solicitada."));
    }

    @Transactional
    public OrderDetail create(Integer customerId, Integer tableId, OrderType type, List<OrderLineInput> items) {
        validateLocation(customerId, tableId, type);
        if (customerId != null && customers.findById(customerId).isEmpty()) {
            throw OrderException.notFound("No se encontró el cliente seleccionado.");
        }
        List<OrderItem> orderItems = validateItems(items);
        OrderAggregate order = OrderAggregate.pending(customerId, tableId, type, orderItems);
        int id = orderPersistence.create(order, orderPersistence.statusId(OrderStatus.PENDING));
        return get(id);
    }

    @Transactional
    public OrderDetail replaceItems(int orderId, List<OrderLineInput> items) {
        OrderAggregate order = orderPersistence.findForUpdate(orderId)
                .orElseThrow(() -> OrderException.notFound("No se encontró la orden solicitada."));
        order.ensureEditable();
        List<OrderItem> validated = validateItems(items);
        orderPersistence.replaceItems(orderId, validated);
        return get(orderId);
    }

    @Transactional
    public void cancel(int orderId) {
        OrderAggregate order = orderPersistence.findForUpdate(orderId)
                .orElseThrow(() -> OrderException.notFound("No se encontró la orden solicitada."));
        if (order.status() == OrderStatus.CANCELED) return;
        OrderAggregate canceled = order.cancel();
        orderPersistence.changeStatus(orderId, orderPersistence.statusId(canceled.status()));
    }

    public OrderPaymentResult confirmPayment(int orderId, String paymentMethod, String idempotencyKey, boolean injectFailure) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toLowerCase();
        if (!PAYMENT_METHODS.contains(method)) throw OrderException.validation("El método de pago seleccionado no es válido.");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw OrderException.validation("La clave de operación es obligatoria y no puede superar 120 caracteres.");
        }

        OrderAggregate order = orderPersistence.find(orderId)
                .orElseThrow(() -> OrderException.notFound("No se encontró la orden solicitada."));
        if (payments.findByOrderId(orderId).isPresent()) throw OrderException.paymentAlreadyConfirmed();
        order.ensureCanPay();
        BigDecimal amount = total(order.items());
        PaymentAuthorization authorization = gateway.authorize(orderId, amount, idempotencyKey);
        if (!authorization.authorized()) throw OrderException.paymentUnavailable();
        if (authorization.amount() == null || authorization.amount().compareTo(amount) != 0) {
            throw OrderException.paymentUnavailable();
        }
        return finalizer.finish(orderId, method, amount, injectFailure);
    }

    private void validateLocation(Integer customerId, Integer tableId, OrderType type) {
        if (type == null) throw OrderException.validation("El tipo de orden es obligatorio.");
        if (type == OrderType.ONLINE && customerId == null) {
            throw OrderException.validation("Las órdenes en línea deben tener un cliente.");
        }
        if (type == OrderType.IN_HOUSE && tableId == null) {
            throw OrderException.validation("Las órdenes en mesa deben indicar la mesa.");
        }
        if (tableId != null && !orderPersistence.tableExists(tableId)) {
            throw OrderException.notFound("No se encontró la mesa seleccionada.");
        }
    }

    private List<OrderItem> validateItems(List<OrderLineInput> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<OrderItem> result = input.stream().map(line -> new OrderItem(line.itemId(), line.quantity())).toList();
        Set<Integer> ids = new HashSet<>();
        for (OrderItem item : result) {
            if (!ids.add(item.menuItemId())) throw OrderException.validation("Cada producto debe aparecer una sola vez en la orden.");
        }
        Map<Integer, MenuItemData> found = menu.findByIds(ids);
        if (found.size() != ids.size()) throw OrderException.notFound("Uno o más productos seleccionados ya no están disponibles.");
        return result;
    }

    private BigDecimal total(List<OrderItem> items) {
        Map<Integer, MenuItemData> products = menu.findByIds(items.stream().map(OrderItem::menuItemId).collect(java.util.stream.Collectors.toSet()));
        BigDecimal amount = items.stream().map(item -> products.get(item.menuItemId()).price()
                .multiply(BigDecimal.valueOf(item.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) throw OrderException.validation("El total supera el límite permitido por la tabla de pagos.");
        return amount;
    }
}
