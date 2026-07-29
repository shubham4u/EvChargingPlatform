package org.evchargingplatform.station.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataStationRepository extends JpaRepository<StationJpaEntity, UUID> {
    boolean existsByExternalReference(String externalReference);
}
