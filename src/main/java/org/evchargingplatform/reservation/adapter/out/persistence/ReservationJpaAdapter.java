package org.evchargingplatform.reservation.adapter.out.persistence;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationEntity;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for the reservation repository port.
 * <p>
 * Maps between the domain {@link Reservation} record and the
 * {@link ReservationEntity} JPA entity.
 */
@Component
public class ReservationJpaAdapter implements ReservationRepository {

    private final ReservationJpaRepository repository;

    public ReservationJpaAdapter(ReservationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        return toDomain(repository.save(toEntity(reservation)));
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Reservation> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Reservation> findExpired(Collection<ReservationStatus> statuses, Instant at) {
        return repository.findByStatusInAndExpirationTimeBefore(statuses, at)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ReservationEntity toEntity(Reservation r) {
        return new ReservationEntity(
                r.id(), r.reservationNumber(), r.stationId(), r.chargerId(),
                r.userId(), r.vehicleId(), r.startTime(), r.expirationTime(),
                r.status(), r.createdAt(), r.updatedAt());
    }

    private Reservation toDomain(ReservationEntity e) {
        return new Reservation(
                e.getId(), e.getReservationNumber(), e.getStationId(), e.getChargerId(),
                e.getUserId(), e.getVehicleId(), e.getStartTime(), e.getExpirationTime(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}