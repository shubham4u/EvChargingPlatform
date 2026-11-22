package org.evchargingplatform.charging.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public record ChargingSessionResponse(
    UUID id,
    String chargingSessionNumber,
    UUID stationId,
    UUID connectorId,
    UUID userId,
    UUID vehicleId,
    UUID reservationId,
    String status,
    Instant startedAt,
    Instant stoppedAt,
    BigDecimal totalEnergyKwh,
    Instant createdAt,
    Instant updatedAt) {

    public static ChargingSessionResponse from(ChargingSession session) {
        return new ChargingSessionResponse(
            session.id(),
            session.chargingSessionNumber(),
            session.stationId(),
            session.connectorId(),
            session.userId(),
            session.vehicleId(),
            session.reservationId(),
            session.status().name(),
            session.startedAt(),
            session.stoppedAt(),
            session.totalEnergyKwh(),
            session.createdAt(),
            session.updatedAt()
        );
    }

}