package org.evchargingplatform.events.analytics.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ProjectionUpdatedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID projectionId,
        String projectionType,
        String sourceEventType
) implements DomainEvent {
    public static final int VERSION = 1;
}

