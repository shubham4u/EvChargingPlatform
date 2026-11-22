package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.station.domain.Connector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorRepository {
    Connector save(Connector connector);

    List<Connector> findByStationId(UUID stationId);

    Optional<Connector> findByIdAndStationId(UUID connectorId, UUID stationId);
}
