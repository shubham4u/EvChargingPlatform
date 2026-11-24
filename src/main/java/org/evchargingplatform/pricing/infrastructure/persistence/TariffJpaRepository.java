package org.evchargingplatform.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TariffJpaRepository extends JpaRepository<TariffEntity, UUID> {

    Optional<TariffEntity> findFirstByStationIdOrderByCreatedAtDesc(UUID stationId);
}
