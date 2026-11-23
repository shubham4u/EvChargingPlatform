package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.domain.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SpringDataStationRepository extends JpaRepository<StationJpaEntity, UUID> {
    boolean existsByExternalReference(String externalReference);

    List<StationJpaEntity> findByStatus(StationStatus status);

    List<StationJpaEntity> findByStatusAndLatitudeBetweenAndLongitudeBetween(
            StationStatus status, BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);
}
