package edu.restaurant.features.catalog.application;

import java.math.BigDecimal;

public record MenuItemData(int id, String name, BigDecimal price, String category) {
}
