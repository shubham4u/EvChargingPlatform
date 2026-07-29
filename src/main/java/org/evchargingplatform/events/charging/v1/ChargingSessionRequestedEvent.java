package org.evchargingplatform.events.charging.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ChargingSessionRequestedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID sessionId,
        UUID userId,
        UUID stationId,
        UUID connectorId,
        UUID reservationId
) implements DomainEvent {
    public static final int VERSION = 1;
}

