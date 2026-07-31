package org.evchargingplatform.reservation.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ReservationExpiredEvent(UUID eventId, int eventVersion, Instant occurredAt, UUID correlationId,
        UUID causationId, UUID reservationId, UUID stationId, UUID connectorId, UUID driverId, UUID vehicleId)
        implements org.evchargingplatform.events.DomainEvent {
    public static final int VERSION = 1;

    public String aggregateType() {
        return "Reservation";
    }

    public UUID aggregateId() {
        return reservationId;
    }
}
