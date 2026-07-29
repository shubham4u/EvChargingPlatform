package org.evchargingplatform.events.reservations.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationExpiredEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID reservationId,
        UUID userId,
        UUID stationId,
        UUID connectorId
) implements DomainEvent {
    public static final int VERSION = 1;
}

