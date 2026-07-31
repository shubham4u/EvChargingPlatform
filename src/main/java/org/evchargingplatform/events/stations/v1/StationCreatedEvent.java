package org.evchargingplatform.events.stations.v1;

import org.evchargingplatform.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record StationCreatedEvent(UUID eventId, int eventVersion, Instant occurredAt,
                                  UUID correlationId, UUID causationId, UUID aggregateId,
                                  String externalReference, String name, String countryCode)
        implements DomainEvent {
    public static final int VERSION = 1;
    @Override public String aggregateType() { return "Station"; }
}
