package edu.restaurant.features.orders.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceOrderItemsRequest(
        @NotNull(message = "La lista de productos es obligatoria.")
        @Size(max = 100, message = "La orden admite hasta 100 productos diferentes.")
        List<@Valid OrderLineRequest> items) {
}
