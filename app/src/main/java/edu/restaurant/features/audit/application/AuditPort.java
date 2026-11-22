package edu.restaurant.features.audit.application;

public interface AuditPort {
    void record(AuditEventRecord event);
}
