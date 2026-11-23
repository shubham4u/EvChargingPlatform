package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.application.port.out.ConnectorOccupancyRepository;
import org.evchargingplatform.station.domain.ConnectorOccupancy;
import org.evchargingplatform.station.infrastructure.persistence.ConnectorOccupancyEntity;
import org.evchargingplatform.station.infrastructure.persistence.SpringDataConnectorOccupancyRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for the connector occupancy read model.
 */
@Component
public class ConnectorOccupancyAdapter implements ConnectorOccupancyRepository {

    private final SpringDataConnectorOccupancyRepository repository;

    public ConnectorOccupancyAdapter(SpringDataConnectorOccupancyRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConnectorOccupancy save(ConnectorOccupancy occupancy) {
        return toDomain(repository.save(new ConnectorOccupancyEntity(occupancy.connectorId(), occupancy.stationId(),
                occupancy.sessionId(), occupancy.startedAt(), occupancy.updatedAt())));
    }

    @Override
    public Optional<ConnectorOccupancy> findByConnectorId(UUID connectorId) {
        return repository.findById(connectorId).map(this::toDomain);
    }

    @Override
    public List<ConnectorOccupancy> findByConnectorIds(Collection<UUID> connectorIds) {
        if (connectorIds.isEmpty()) {
            return List.of();
        }
        return repository.findByConnectorIdIn(connectorIds).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByConnectorId(UUID connectorId) {
        repository.deleteById(connectorId);
    }

    private ConnectorOccupancy toDomain(ConnectorOccupancyEntity e) {
        return new ConnectorOccupancy(e.getConnectorId(), e.getStationId(), e.getSessionId(), e.getStartedAt(), e.getUpdatedAt());
    }
}
