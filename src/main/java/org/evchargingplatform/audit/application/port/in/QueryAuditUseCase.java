package org.evchargingplatform.audit.application.port.in;

import org.evchargingplatform.audit.domain.AuditEvent;

import java.util.List;

public interface QueryAuditUseCase {
    /** Every filter is optional; results are in the order the events occurred, at most {@code limit}. */
    List<AuditEvent> find(String key, String eventType, String traceId, int limit);
}
