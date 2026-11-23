package org.evchargingplatform.station.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataConnectorRepository extends JpaRepository<ConnectorJpaEntity, UUID> {
    List<ConnectorJpaEntity> findByStationIdOrderByCreatedAt(UUID stationId);

    List<ConnectorJpaEntity> findByStationIdInOrderByCreatedAt(Collection<UUID> stationIds);

    Optional<ConnectorJpaEntity> findByIdAndStationId(UUID id, UUID stationId);
}
