package org.evchargingplatform.station.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the connector occupancy read model, one row per connector currently in a session.
 */
@Entity
@Table(name = "connector_occupancy")
public class ConnectorOccupancyEntity {
    @Id
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectorOccupancyEntity() {
    }

    public ConnectorOccupancyEntity(UUID connectorId, UUID stationId, UUID sessionId, Instant startedAt, Instant updatedAt) {
        this.connectorId = connectorId;
        this.stationId = stationId;
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getConnectorId() { return connectorId; }
    public UUID getStationId() { return stationId; }
    public UUID getSessionId() { return sessionId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
