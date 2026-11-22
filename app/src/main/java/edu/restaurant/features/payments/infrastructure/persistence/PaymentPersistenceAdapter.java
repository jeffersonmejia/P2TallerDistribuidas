package edu.restaurant.features.payments.infrastructure.persistence;

import edu.restaurant.features.payments.application.PaymentPersistencePort;
import edu.restaurant.features.payments.application.PaymentReceipt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceAdapter implements PaymentPersistencePort {
    private final PaymentJpaRepository repository;

    public PaymentPersistenceAdapter(PaymentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentReceipt> findByOrderId(int orderId) {
        return repository.findByOrderId(orderId).map(this::toReceipt);
    }

    @Override
    public PaymentReceipt save(int orderId, BigDecimal amount, LocalDate paymentDate, String method) {
        return toReceipt(repository.saveAndFlush(new PaymentEntity(amount, paymentDate, method, orderId)));
    }

    private PaymentReceipt toReceipt(PaymentEntity entity) {
        return new PaymentReceipt(entity.getId(), entity.getOrderId(), entity.getAmount(), entity.getPaymentDate(), entity.getPaymentMethod());
    }
}
