package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.domain.Connector;

import java.time.Instant;
import java.util.UUID;

public record ConnectorResponse(UUID id, UUID stationId, String connectorType, int maxPowerKw, Instant createdAt) {
    static ConnectorResponse from(Connector connector) {
        return new ConnectorResponse(connector.id(), connector.stationId(), connector.connectorType().name(),
                connector.maxPowerKw(), connector.createdAt());
    }
}
