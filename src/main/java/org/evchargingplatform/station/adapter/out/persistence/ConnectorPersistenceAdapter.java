package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.application.port.out.ConnectorRepository;
import org.evchargingplatform.station.domain.Connector;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ConnectorPersistenceAdapter implements ConnectorRepository {

    private final SpringDataConnectorRepository repository;

    ConnectorPersistenceAdapter(SpringDataConnectorRepository repository) {
        this.repository = repository;
    }

    @Override
    public Connector save(Connector connector) {
        return toDomain(repository.save(toEntity(connector)));
    }

    @Override
    public List<Connector> findByStationId(UUID stationId) {
        return repository.findByStationIdOrderByCreatedAt(stationId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Connector> findByStationIds(Collection<UUID> stationIds) {
        if (stationIds.isEmpty()) {
            return List.of();
        }
        return repository.findByStationIdInOrderByCreatedAt(stationIds).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Connector> findByIdAndStationId(UUID connectorId, UUID stationId) {
        return repository.findByIdAndStationId(connectorId, stationId).map(this::toDomain);
    }

    private ConnectorJpaEntity toEntity(Connector connector) {
        return new ConnectorJpaEntity(connector.id(), connector.stationId(), connector.connectorType(),
                connector.maxPowerKw(), connector.createdAt());
    }

    private Connector toDomain(ConnectorJpaEntity entity) {
        return new Connector(entity.id, entity.stationId, entity.connectorType, entity.maxPowerKw, entity.createdAt);
    }
}
