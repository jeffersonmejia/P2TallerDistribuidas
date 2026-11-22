package edu.restaurant.features.orders.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderSummary(
        int id,
        LocalDate orderDate,
        String customerName,
        String orderType,
        Integer tableId,
        String status,
        BigDecimal total,
        boolean paid) {
}
