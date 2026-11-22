package org.evchargingplatform.charging.infrastructure.persistence;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionJpaRepository 
extends JpaRepository<ChargingSessionEntity, UUID> {

    boolean existsByConnectorIdAndStatusIn(UUID connectorId, Collection<String> statuses);

    boolean existsByVehicleIdAndStatusIn(UUID vehicleId, Collection<String> statuses);
}
