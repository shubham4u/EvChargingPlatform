package org.evchargingplatform.events.stations.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StationRegisteredEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID stationId,
        String externalRef,
        String name,
        String countryCode
) implements DomainEvent {
    public static final int VERSION = 1;
}

