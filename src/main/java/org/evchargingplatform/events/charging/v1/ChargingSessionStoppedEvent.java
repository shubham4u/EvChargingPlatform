package org.evchargingplatform.events.charging.v1;

import org.evchargingplatform.events.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargingSessionStoppedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID sessionId,
        UUID userId,
        UUID stationId,
        UUID connectorId,
        Instant stoppedAt,
        BigDecimal totalEnergyKwh
) implements DomainEvent {
    public static final int VERSION = 1;
}

