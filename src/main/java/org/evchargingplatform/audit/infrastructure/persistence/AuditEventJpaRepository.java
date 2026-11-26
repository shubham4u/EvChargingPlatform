package org.evchargingplatform.audit.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    @Query("""
            SELECT e FROM AuditEventEntity e
            WHERE (:key IS NULL OR e.key = :key)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:traceId IS NULL OR e.traceId = :traceId)
            ORDER BY e.occurredAt, e.recordedAt
            """)
    List<AuditEventEntity> search(@Param("key") String key, @Param("eventType") String eventType,
                                  @Param("traceId") String traceId, Pageable page);
}
