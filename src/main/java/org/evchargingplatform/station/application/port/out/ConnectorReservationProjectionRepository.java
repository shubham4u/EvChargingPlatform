package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.station.domain.ConnectorReservationProjection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for persisting connector reservation projections.
 * <p>
 * Infrastructure adapters implement this port to store projections
 * in the Station Service's own database.
 */
public interface ConnectorReservationProjectionRepository {

    ConnectorReservationProjection save(ConnectorReservationProjection projection);

    Optional<ConnectorReservationProjection> findByReservationId(UUID reservationId);

    List<ConnectorReservationProjection> findByStationIdAndStatus(UUID stationId,
            ConnectorReservationProjection.ReservationProjectionStatus status);

    boolean existsByReservationId(UUID reservationId);
}