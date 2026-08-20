package org.evchargingplatform.charging.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record ChargingSession(UUID id, String chargingSessionNumber, UUID stationId, UUID connectorId, UUID userId,
        UUID vehicleId, UUID reservationId, ChargingSessionStatus status, Instant startedAt, Instant stoppedAt,
        BigDecimal totalEnergyKwh, Instant createdAt, Instant updatedAt) implements Serializable {

    public static ChargingSession create(UUID stationId, UUID connectorId, UUID userId, UUID vehicleId,
            UUID reservationId, Clock clock) {
        Instant now = Instant.now(clock);
        return new ChargingSession(UUID.randomUUID(), "Charging" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                stationId, connectorId, userId, vehicleId, reservationId, ChargingSessionStatus.REQUESTED,
                null, null, null, now, now);
    }

    public ChargingSession start(Clock clock) {
        if (status != ChargingSessionStatus.REQUESTED) {
            throw new IllegalStateException("Charging session can only be started from REQUESTED status");
        }
        return copy(ChargingSessionStatus.ACTIVE, Instant.now(clock), stoppedAt, totalEnergyKwh, clock);
    }

    public ChargingSession stop(BigDecimal totalEnergyKwh, Clock clock) {
        if (status != ChargingSessionStatus.ACTIVE) {
            throw new IllegalStateException("Charging session can only be stopped from ACTIVE status");
        }
        return copy(ChargingSessionStatus.STOPPED, startedAt, Instant.now(clock), totalEnergyKwh, clock);
    }

    private ChargingSession copy(ChargingSessionStatus newStatus, Instant newStartedAt, Instant newStoppedAt,
            BigDecimal newTotalEnergyKwh, Clock clock) {
        return new ChargingSession(id, chargingSessionNumber, stationId, connectorId, userId, vehicleId,
                reservationId, newStatus, newStartedAt, newStoppedAt, newTotalEnergyKwh, createdAt, Instant.now(clock));
    }
}

