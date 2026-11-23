package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.station.domain.ConnectorOccupancy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorOccupancyRepository {
    ConnectorOccupancy save(ConnectorOccupancy occupancy);

    Optional<ConnectorOccupancy> findByConnectorId(UUID connectorId);

    List<ConnectorOccupancy> findByConnectorIds(Collection<UUID> connectorIds);

    void deleteByConnectorId(UUID connectorId);
}
