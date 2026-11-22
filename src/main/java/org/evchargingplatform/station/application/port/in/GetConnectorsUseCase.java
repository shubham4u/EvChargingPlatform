package org.evchargingplatform.station.application.port.in;

import java.util.List;
import java.util.UUID;

import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.exception.ConnectorNotFoundException;

/**
 * Query port for connectors, also called in-process by reservation and charging.
 */
public interface GetConnectorsUseCase {
    List<Connector> findByStation(UUID stationId);

    /**
     * @throws ConnectorNotFoundException if the connector does not exist or is at a different station
     */
    Connector findAtStation(UUID stationId, UUID connectorId);
}
