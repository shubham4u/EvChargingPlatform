package org.evchargingplatform.reservation.adapter.out.persistence;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationEntity;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationJpaRepository;
import org.evchargingplatform.reservation.domain.exception.ConnectorAlreadyReservedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
        // Flush now so a lost race on the overlap exclusion constraint (V14, Postgres) surfaces as the usual 409.
        try {
            return toDomain(repository.saveAndFlush(toEntity(reservation)));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT)
                    .contains("ex_reservations_charger_window")) {
                throw new ConnectorAlreadyReservedException();
            }
            throw e;
        }
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
    public List<Reservation> findByUserId(UUID userId) {
        return repository.findByUserIdOrderByStartTimeDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Reservation> findExpired(Collection<ReservationStatus> statuses, Instant at) {
        return repository.findByStatusInAndExpirationTimeBefore(statuses, at)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsOverlapping(UUID chargerId, Collection<ReservationStatus> statuses, Instant start, Instant end) {
        return repository.existsByChargerIdAndStatusInAndStartTimeBeforeAndExpirationTimeAfter(chargerId, statuses, end, start);
    }

    private ReservationEntity toEntity(Reservation r) {
        return new ReservationEntity(
                r.id(), r.reservationNumber(), r.stationId(), r.chargerId(),
                r.userId(), r.vehicleId(), r.startTime(), r.expirationTime(),
                r.status(), r.createdAt(), r.updatedAt(), currentVersion(r.id()));
    }

    // The domain record carries no version, so reuse the persisted one. Rebuilding the entity
    // with version 0 makes Hibernate reject every update after the first as a stale write.
    private long currentVersion(UUID id) {
        return repository.findById(id).map(ReservationEntity::getVersion).orElse(0L);
    }

    private Reservation toDomain(ReservationEntity e) {
        return new Reservation(
                e.getId(), e.getReservationNumber(), e.getStationId(), e.getChargerId(),
                e.getUserId(), e.getVehicleId(), e.getStartTime(), e.getExpirationTime(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}