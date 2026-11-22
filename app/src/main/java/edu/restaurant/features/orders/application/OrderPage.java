package edu.restaurant.features.orders.application;

import java.util.List;

public record OrderPage(List<OrderSummary> content, int page, int size, long totalElements, int totalPages) {
    public OrderPage {
        content = List.copyOf(content);
    }
}
