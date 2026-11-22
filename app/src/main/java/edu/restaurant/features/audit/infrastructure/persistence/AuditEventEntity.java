package edu.restaurant.features.audit.infrastructure.persistence;

import edu.restaurant.features.audit.application.AuditEventRecord;
import edu.restaurant.features.audit.application.AuditEventView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "Audit_Events")
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long id;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "result", nullable = false, length = 30)
    private String result;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "amount", precision = 6, scale = 2)
    private BigDecimal amount;

    @Column(name = "actor", nullable = false, length = 40)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, length = 80)
    private String correlationId;

    @Column(name = "operation_key", length = 120)
    private String operationKey;

    protected AuditEventEntity() {}

    AuditEventEntity(AuditEventRecord record) {
        this.action = record.action();
        this.result = record.result();
        this.orderId = record.orderId();
        this.paymentId = record.paymentId();
        this.amount = record.amount();
        this.actor = record.actor();
        this.occurredAt = record.occurredAt();
        this.correlationId = record.correlationId();
        this.operationKey = record.operationKey();
    }

    AuditEventView toView() {
        return new AuditEventView(id, action, result, orderId, paymentId, amount, actor, occurredAt, correlationId, operationKey);
    }
}
