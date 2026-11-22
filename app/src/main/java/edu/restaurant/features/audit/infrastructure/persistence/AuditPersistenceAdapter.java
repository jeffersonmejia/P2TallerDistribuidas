package edu.restaurant.features.audit.infrastructure.persistence;

import edu.restaurant.features.audit.application.AuditEventRecord;
import edu.restaurant.features.audit.application.AuditEventView;
import edu.restaurant.features.audit.application.AuditPort;
import edu.restaurant.features.audit.application.AuditReadPort;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditPersistenceAdapter implements AuditPort, AuditReadPort {
    private final AuditJpaRepository repository;

    public AuditPersistenceAdapter(AuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventRecord event) {
        repository.saveAndFlush(new AuditEventEntity(event));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventView> latest(int limit) {
        return repository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(AuditEventEntity::toView).toList();
    }
}
