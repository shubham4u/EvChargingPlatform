package org.evchargingplatform.audit.application.port.out;

import org.evchargingplatform.audit.domain.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository {
    AuditEvent save(AuditEvent event);

    boolean exists(UUID eventId);

    List<AuditEvent> find(String key, String eventType, String traceId, int limit);
}
