package edu.restaurant.features.orders.presentation;

import jakarta.validation.constraints.Min;

public record OrderLineRequest(
        @Min(value = 1, message = "Selecciona un producto válido.") int itemId,
        @Min(value = 1, message = "La cantidad debe ser mayor que cero.") int quantity) {
}
