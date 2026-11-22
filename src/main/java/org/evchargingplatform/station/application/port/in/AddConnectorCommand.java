package org.evchargingplatform.station.application.port.in;

import java.util.UUID;

import org.evchargingplatform.station.domain.ConnectorType;

public record AddConnectorCommand(
        UUID stationId,
        ConnectorType connectorType,
        int maxPowerKw) {
}
