package org.evchargingplatform.station.infrastructure.persistence;

import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataConnectorReservationProjectionRepository
        extends JpaRepository<ConnectorReservationProjectionEntity, UUID> {

    @Query("SELECT e FROM ConnectorReservationProjectionEntity e WHERE e.stationId = :stationId AND e.status = :status")
    List<ConnectorReservationProjectionEntity> findByStationIdAndStatus(
            @Param("stationId") UUID stationId,
            @Param("status") ConnectorReservationProjection.ReservationProjectionStatus status);
}