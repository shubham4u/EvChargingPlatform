package org.evchargingplatform.events.stations.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ConnectorUpdatedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID stationId,
        UUID connectorId,
        String connectorType,
        Integer maxPowerKw,
        String status
) implements DomainEvent {
    public static final int VERSION = 1;
}

