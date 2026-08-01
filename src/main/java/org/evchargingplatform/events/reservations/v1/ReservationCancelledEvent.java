package org.evchargingplatform.events.reservations.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published when a reservation is cancelled.
 */
public record ReservationCancelledEvent(
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