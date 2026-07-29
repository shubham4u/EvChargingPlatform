package org.evchargingplatform.events.charging.v1;

import org.evchargingplatform.events.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterValueRecordedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID sessionId,
        UUID stationId,
        UUID connectorId,
        BigDecimal energyKwh,
        BigDecimal powerKw
) implements DomainEvent {
    public static final int VERSION = 1;
}

