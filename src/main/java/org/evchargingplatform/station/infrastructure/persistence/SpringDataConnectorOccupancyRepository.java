package org.evchargingplatform.station.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataConnectorOccupancyRepository extends JpaRepository<ConnectorOccupancyEntity, UUID> {
    List<ConnectorOccupancyEntity> findByConnectorIdIn(Collection<UUID> connectorIds);
}
