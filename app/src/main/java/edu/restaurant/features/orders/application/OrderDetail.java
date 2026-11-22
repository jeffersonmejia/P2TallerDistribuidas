package edu.restaurant.features.orders.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderDetail(
        int id,
        LocalDate orderDate,
        Integer customerId,
        String customerName,
        Integer tableId,
        String orderType,
        String status,
        List<OrderLineView> items,
        BigDecimal total,
        BigDecimal paidAmount,
        String paymentMethod) {
    public OrderDetail {
        items = List.copyOf(items);
    }
}
