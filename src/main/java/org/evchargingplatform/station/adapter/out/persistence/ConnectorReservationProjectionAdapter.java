package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.evchargingplatform.station.infrastructure.persistence.ConnectorReservationProjectionEntity;
import org.evchargingplatform.station.infrastructure.persistence.SpringDataConnectorReservationProjectionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for the connector reservation projection.
 * <p>
 * Maps between the domain projection record and the JPA entity.
 */
@Component
public class ConnectorReservationProjectionAdapter implements ConnectorReservationProjectionRepository {

    private final SpringDataConnectorReservationProjectionRepository repository;

    public ConnectorReservationProjectionAdapter(SpringDataConnectorReservationProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConnectorReservationProjection save(ConnectorReservationProjection projection) {
        ConnectorReservationProjectionEntity entity = toEntity(projection);
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<ConnectorReservationProjection> findByReservationId(UUID reservationId) {
        return repository.findById(reservationId).map(this::toDomain);
    }

    @Override
    public List<ConnectorReservationProjection> findByStationIdAndStatus(
            UUID stationId, ConnectorReservationProjection.ReservationProjectionStatus status) {
        return repository.findByStationIdAndStatus(stationId, status).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByReservationId(UUID reservationId) {
        return repository.existsById(reservationId);
    }

    private ConnectorReservationProjectionEntity toEntity(ConnectorReservationProjection p) {
        return new ConnectorReservationProjectionEntity(
                p.reservationId(), p.stationId(), p.connectorId(), p.userId(),
                p.status(), p.expiresAt(), p.updatedAt());
    }

    private ConnectorReservationProjection toDomain(ConnectorReservationProjectionEntity e) {
        return new ConnectorReservationProjection(
                e.getReservationId(), e.getStationId(), e.getConnectorId(), e.getUserId(),
                e.getStatus(), e.getExpiresAt(), e.getUpdatedAt());
    }
}