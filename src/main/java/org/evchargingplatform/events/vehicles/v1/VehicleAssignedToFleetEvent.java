package org.evchargingplatform.events.vehicles.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record VehicleAssignedToFleetEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID vehicleId,
        UUID fleetId
) implements DomainEvent {
    public static final int VERSION = 1;
}

