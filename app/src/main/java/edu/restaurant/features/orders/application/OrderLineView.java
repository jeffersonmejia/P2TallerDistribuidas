package edu.restaurant.features.orders.application;

import java.math.BigDecimal;

public record OrderLineView(int itemId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
