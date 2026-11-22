package edu.restaurant.features.audit.presentation;

import edu.restaurant.features.audit.application.AuditEventView;
import edu.restaurant.features.audit.application.AuditReadPort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/audit/events")
public class AuditController {
    private final AuditReadPort audit;

    public AuditController(AuditReadPort audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEventResponse> latest(@RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return audit.latest(limit).stream().map(this::toResponse).toList();
    }

    private AuditEventResponse toResponse(AuditEventView event) {
        return new AuditEventResponse(event.id(), event.action(), event.result(), event.orderId(), event.paymentId(),
                event.amount(), event.actor(), event.occurredAt(), event.correlationId(), event.operationKey());
    }
}
