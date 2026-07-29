package org.evchargingplatform.events.charging.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ChargingSessionStartedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID sessionId,
        UUID userId,
        UUID stationId,
        UUID connectorId,
        Instant startedAt
) implements DomainEvent {
    public static final int VERSION = 1;
}

