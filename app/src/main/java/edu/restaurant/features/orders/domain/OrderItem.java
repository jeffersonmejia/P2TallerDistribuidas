package edu.restaurant.features.orders.domain;

public record OrderItem(int menuItemId, int quantity) {
    public OrderItem {
        if (menuItemId <= 0) throw OrderException.validation("El producto seleccionado no es válido.");
        if (quantity <= 0) throw OrderException.validation("La cantidad debe ser mayor que cero.");
    }
}
