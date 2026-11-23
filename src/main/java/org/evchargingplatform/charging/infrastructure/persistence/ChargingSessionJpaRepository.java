package org.evchargingplatform.charging.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionJpaRepository 
extends JpaRepository<ChargingSessionEntity, UUID> {

    List<ChargingSessionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ChargingSessionEntity> findByStationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID stationId, Instant from, Instant to);

    boolean existsByConnectorIdAndStatusIn(UUID connectorId, Collection<String> statuses);

    boolean existsByVehicleIdAndStatusIn(UUID vehicleId, Collection<String> statuses);
}
