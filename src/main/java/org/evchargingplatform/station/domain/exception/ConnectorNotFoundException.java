package org.evchargingplatform.station.domain.exception;

import java.util.UUID;

public class ConnectorNotFoundException extends RuntimeException {
    public ConnectorNotFoundException(UUID stationId, UUID connectorId) {
        super("Connector " + connectorId + " not found at station " + stationId);
    }
}
