package edu.restaurant.features.orders.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        Integer customerId,
        Integer tableId,
        @NotBlank(message = "El tipo de orden es obligatorio.") @Size(max = 20) String orderType,
        @Size(max = 100, message = "La orden admite hasta 100 productos diferentes.") List<@Valid OrderLineRequest> items) {
}
