package org.evchargingplatform.events.maintenance.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StationFaultDetectedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID stationId,
        UUID connectorId,
        String faultCode,
        String severity,
        String description
) implements DomainEvent {
    public static final int VERSION = 1;
}

