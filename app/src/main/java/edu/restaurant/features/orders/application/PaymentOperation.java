package edu.restaurant.features.orders.application;

public interface PaymentOperation {
    OrderPaymentResult confirmPayment(int orderId, String paymentMethod, String idempotencyKey, boolean injectFailure);
}
