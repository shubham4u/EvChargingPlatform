package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.exception.StationNotFoundException;

import java.util.List;
import java.util.UUID;

public interface GetConnectorAvailabilityUseCase {
    /**
     * @throws StationNotFoundException if the station does not exist
     */
    List<ConnectorStatus> findByStation(UUID stationId);
}
