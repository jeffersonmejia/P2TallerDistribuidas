package edu.restaurant.features.orders.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmOrderPaymentRequest(
        @NotBlank(message = "El método de pago es obligatorio.") @Size(max = 40) String paymentMethod,
        @NotBlank(message = "La clave de operación es obligatoria.") @Size(max = 120) String idempotencyKey) {
}
