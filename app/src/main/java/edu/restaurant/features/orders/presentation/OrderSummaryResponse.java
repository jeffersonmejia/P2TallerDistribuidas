package edu.restaurant.features.orders.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderSummaryResponse(int id, LocalDate orderDate, String customerName, String orderType,
                                   Integer tableId, String status, BigDecimal total, boolean paid) {
}
