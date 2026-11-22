package edu.restaurant.features.audit.application;

import java.util.List;

public interface AuditReadPort {
    List<AuditEventView> latest(int limit);
}
