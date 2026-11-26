package org.evchargingplatform.audit.adapter.out.persistence;

import org.evchargingplatform.audit.application.port.out.AuditEventRepository;
import org.evchargingplatform.audit.domain.AuditEvent;
import org.evchargingplatform.audit.infrastructure.persistence.AuditEventEntity;
import org.evchargingplatform.audit.infrastructure.persistence.AuditEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * JPA persistence adapter for the append-only audit store.
 */
@Component
public class AuditEventJpaAdapter implements AuditEventRepository {

    private final AuditEventJpaRepository repository;

    public AuditEventJpaAdapter(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEvent save(AuditEvent e) {
        return toDomain(repository.save(new AuditEventEntity(e.eventId(), e.topic(), e.partition(), e.offset(), e.key(),
                e.eventType(), e.occurredAt(), e.traceId(), e.payload(), e.recordedAt())));
    }

    @Override
    public boolean exists(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public List<AuditEvent> find(String key, String eventType, String traceId, int limit) {
        return repository.search(key, eventType, traceId, PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    private AuditEvent toDomain(AuditEventEntity e) {
        return new AuditEvent(e.eventId(), e.topic(), e.partition(), e.offset(), e.key(), e.eventType(), e.occurredAt(),
                e.traceId(), e.payload(), e.recordedAt());
    }
}
