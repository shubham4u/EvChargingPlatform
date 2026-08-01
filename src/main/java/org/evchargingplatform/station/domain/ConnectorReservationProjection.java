package org.evchargingplatform.station.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection model tracking the reservation state of a connector.
 * <p>
 * This is a read-model maintained by consuming reservation lifecycle events.
 * It is not part of the Reservation aggregate — it is the Station Service's
 * local view of which connectors are currently reserved.
 */
public record ConnectorReservationProjection(
        UUID reservationId,
        UUID stationId,
        UUID connectorId,
        UUID userId,
        ReservationProjectionStatus status,
        Instant expiresAt,
        Instant updatedAt) implements Serializable {

    public enum ReservationProjectionStatus {
        RESERVED, RELEASED
    }

    public static ConnectorReservationProjection reserved(
            UUID reservationId, UUID stationId, UUID connectorId, UUID userId, Instant expiresAt) {
        return new ConnectorReservationProjection(
                reservationId, stationId, connectorId, userId,
                ReservationProjectionStatus.RESERVED, expiresAt, Instant.now());
    }

    public ConnectorReservationProjection released() {
        return new ConnectorReservationProjection(
                reservationId, stationId, connectorId, userId,
                ReservationProjectionStatus.RELEASED, expiresAt, Instant.now());
    }

    public boolean isReserved() {
        return status == ReservationProjectionStatus.RESERVED;
    }
}