package org.evchargingplatform.vehicle.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleJpaRepository extends JpaRepository<VehicleEntity, UUID> {

    List<VehicleEntity> findByOwnerUserId(UUID ownerUserId);

    boolean existsByVin(String vin);
}
