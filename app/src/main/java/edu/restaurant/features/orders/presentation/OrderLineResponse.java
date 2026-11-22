package edu.restaurant.features.orders.presentation;

import java.math.BigDecimal;

public record OrderLineResponse(int itemId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
