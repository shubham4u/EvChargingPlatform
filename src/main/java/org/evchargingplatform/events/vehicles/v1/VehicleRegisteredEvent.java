package org.evchargingplatform.events.vehicles.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record VehicleRegisteredEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID vehicleId,
        UUID ownerUserId,
        String vin,
        String make,
        String model
) implements DomainEvent {
    public static final int VERSION = 1;
}

