package org.evchargingplatform.audit.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable copy of one domain event as it appeared on Kafka (FR-10 audit history).
 * <p>
 * {@link #key} is the Kafka key, i.e. the aggregate the event is about (session, reservation, user, ...),
 * so "everything that happened to session X" is one query. {@link #traceId} is the W3C trace the event
 * was published under, linking it back to the API request in the tracing backend (Jaeger).
 */
public record AuditEvent(UUID eventId, String topic, int partition, long offset, String key, String eventType,
        Instant occurredAt, String traceId, String payload, Instant recordedAt) implements Serializable {
}
