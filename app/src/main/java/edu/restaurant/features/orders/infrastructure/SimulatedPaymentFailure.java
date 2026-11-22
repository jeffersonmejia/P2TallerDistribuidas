package edu.restaurant.features.orders.infrastructure;

public final class SimulatedPaymentFailure extends RuntimeException {
    public SimulatedPaymentFailure() {
        super("Fallo simulado después de escribir el pago y completar la orden.");
    }
}
