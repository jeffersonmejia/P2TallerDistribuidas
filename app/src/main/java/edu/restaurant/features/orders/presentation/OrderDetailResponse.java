package edu.restaurant.features.orders.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderDetailResponse(int id, LocalDate orderDate, Integer customerId, String customerName, Integer tableId,
                                  String orderType, String status, List<OrderLineResponse> items, BigDecimal total,
                                  BigDecimal paidAmount, String paymentMethod) {
}
