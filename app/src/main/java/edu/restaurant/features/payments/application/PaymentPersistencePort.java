package edu.restaurant.features.payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface PaymentPersistencePort {
    Optional<PaymentReceipt> findByOrderId(int orderId);
    PaymentReceipt save(int orderId, BigDecimal amount, LocalDate paymentDate, String method);
}
