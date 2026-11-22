package edu.restaurant.features.orders.domain;

public final class OrderException extends RuntimeException {
    public enum Reason { VALIDATION, NOT_FOUND, STATE_CONFLICT, PAYMENT_UNAVAILABLE, PAYMENT_ALREADY_CONFIRMED }

    private final Reason reason;

    private OrderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static OrderException validation(String message) {
        return new OrderException(Reason.VALIDATION, message);
    }

    public static OrderException notFound(String message) {
        return new OrderException(Reason.NOT_FOUND, message);
    }

    public static OrderException conflict(String message) {
        return new OrderException(Reason.STATE_CONFLICT, message);
    }

    public static OrderException paymentUnavailable() {
        return new OrderException(Reason.PAYMENT_UNAVAILABLE, "No fue posible autorizar el pago simulado.");
    }

    public static OrderException paymentAlreadyConfirmed() {
        return new OrderException(Reason.PAYMENT_ALREADY_CONFIRMED, "La orden ya tiene un pago confirmado.");
    }
}
