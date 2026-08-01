package org.evchargingplatform.station.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the connector reservation projection table.
 * <p>
 * Stores the Station Service's local view of reservation states
 * consumed from Kafka events.
 */
@Entity
@Table(name = "connector_reservation_projections")
public class ConnectorReservationProjectionEntity {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConnectorReservationProjection.ReservationProjectionStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectorReservationProjectionEntity() {
    }

    public ConnectorReservationProjectionEntity(
            UUID reservationId, UUID stationId, UUID connectorId, UUID userId,
            ConnectorReservationProjection.ReservationProjectionStatus status,
            Instant expiresAt, Instant updatedAt) {
        this.reservationId = reservationId;
        this.stationId = stationId;
        this.connectorId = connectorId;
        this.userId = userId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getStationId() { return stationId; }
    public UUID getConnectorId() { return connectorId; }
    public UUID getUserId() { return userId; }
    public ConnectorReservationProjection.ReservationProjectionStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}