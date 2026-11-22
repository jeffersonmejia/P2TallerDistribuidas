package edu.restaurant.features.orders.domain;

import java.time.LocalDate;
import java.util.List;

public record OrderAggregate(
        Integer id,
        LocalDate orderDate,
        Integer customerId,
        Integer tableId,
        OrderType type,
        OrderStatus status,
        List<OrderItem> items) {

    public OrderAggregate {
        items = List.copyOf(items);
        if (orderDate == null || type == null || status == null) {
            throw OrderException.validation("La orden tiene datos incompletos.");
        }
        if (customerId == null && tableId == null) {
            throw OrderException.validation("La orden debe tener un cliente o una mesa.");
        }
    }

    public static OrderAggregate pending(Integer customerId, Integer tableId, OrderType type, List<OrderItem> items) {
        return new OrderAggregate(null, LocalDate.now(), customerId, tableId, type, OrderStatus.PENDING, items);
    }

    public void ensureEditable() {
        if (status != OrderStatus.PENDING) {
            throw OrderException.conflict("Solo se pueden editar órdenes pendientes.");
        }
    }

    public void ensureCanPay() {
        if (status != OrderStatus.PENDING) {
            throw OrderException.conflict("Solo se pueden pagar órdenes pendientes.");
        }
        if (items.isEmpty()) {
            throw OrderException.validation("Agrega al menos un producto antes de pagar.");
        }
    }

    public OrderAggregate withItems(List<OrderItem> replacement) {
        ensureEditable();
        return new OrderAggregate(id, orderDate, customerId, tableId, type, status, replacement);
    }

    public OrderAggregate cancel() {
        ensureEditable();
        return new OrderAggregate(id, orderDate, customerId, tableId, type, OrderStatus.CANCELED, items);
    }

    public OrderAggregate complete() {
        ensureCanPay();
        return new OrderAggregate(id, orderDate, customerId, tableId, type, OrderStatus.COMPLETED, items);
    }
}
