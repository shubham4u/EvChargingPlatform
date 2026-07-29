package org.evchargingplatform.events.reservations.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID reservationId,
        UUID userId,
        UUID stationId,
        UUID connectorId,
        Instant expiresAt
) implements DomainEvent {
    public static final int VERSION = 1;
}

