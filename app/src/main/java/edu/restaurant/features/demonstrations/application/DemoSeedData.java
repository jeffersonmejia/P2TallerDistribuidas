package edu.restaurant.features.demonstrations.application;

import java.math.BigDecimal;

public record DemoSeedData(int customerId, int menuItemId, String menuItemName, BigDecimal unitPrice) {
}
