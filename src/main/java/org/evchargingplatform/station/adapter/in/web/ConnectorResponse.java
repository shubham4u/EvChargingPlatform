package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorAvailability;

import java.time.Instant;
import java.util.UUID;

/**
 * @param status AVAILABLE, RESERVED or OCCUPIED, from the station's read models (may lag a few hundred ms)
 */
public record ConnectorResponse(UUID id, UUID stationId, String connectorType, int maxPowerKw, String status,
                                Instant createdAt) {
    static ConnectorResponse from(ConnectorStatus status) {
        return of(status.connector(), status.availability());
    }

    // A connector that was just added has no sessions or holds yet.
    static ConnectorResponse from(Connector connector) {
        return of(connector, ConnectorAvailability.AVAILABLE);
    }

    private static ConnectorResponse of(Connector connector, ConnectorAvailability availability) {
        return new ConnectorResponse(connector.id(), connector.stationId(), connector.connectorType().name(),
                connector.maxPowerKw(), availability.name(), connector.createdAt());
    }
}
