package edu.restaurant.features.catalog.presentation;

import java.math.BigDecimal;

public record MenuItemResponse(int id, String name, BigDecimal price, String category) {
}
