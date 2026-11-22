package org.evchargingplatform.station.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.evchargingplatform.station.domain.ConnectorType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connectors")
public class ConnectorJpaEntity {
    @Id
    UUID id;

    @Column(name = "station_id", nullable = false)
    UUID stationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    ConnectorType connectorType;

    @Column(name = "max_power_kw", nullable = false)
    int maxPowerKw;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected ConnectorJpaEntity() {
    }

    ConnectorJpaEntity(UUID id, UUID stationId, ConnectorType connectorType, int maxPowerKw, Instant createdAt) {
        this.id = id;
        this.stationId = stationId;
        this.connectorType = connectorType;
        this.maxPowerKw = maxPowerKw;
        this.createdAt = createdAt;
    }
}
