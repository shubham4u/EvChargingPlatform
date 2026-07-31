package org.evchargingplatform.reservation.application;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationEntity;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ReservationService {
    private final ReservationJpaRepository repo;
    private final Clock clock = Clock.systemUTC();

    public ReservationService(ReservationJpaRepository r) {
        repo = r;
    }

    @Transactional
    public Reservation create(UUID s, UUID c, UUID u, UUID v, Instant start) {
        return save(Reservation.create(s, c, u, v, start, clock));
    }

    @Transactional
    public Reservation cancel(UUID id) {
        return save(get(id).cancel(clock));
    }

    @Transactional
    public Reservation complete(UUID id) {
        return save(get(id).complete(clock));
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return toDomain(
                repo.findById(id).orElseThrow(() -> new NoSuchElementException("Reservation not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<Reservation> all() {
        return repo.findAll().stream().map(this::toDomain).toList();
    }

    @Scheduled(fixedDelayString = "${reservation.expiration.poll-ms:60000}")
    @Transactional
    public void expireReservations() {
        repo.findByStatusInAndExpirationTimeBefore(List.of(ReservationStatus.CREATED, ReservationStatus.ACTIVE),
                Instant.now(clock)).forEach(e -> save(toDomain(e).expire(clock)));
    }

    private Reservation save(Reservation r) {
        return toDomain(
                repo.save(new ReservationEntity(r.id(), r.reservationNumber(), r.stationId(), r.chargerId(), r.userId(),
                        r.vehicleId(), r.startTime(), r.expirationTime(), r.status(), r.createdAt(), r.updatedAt())));
    }

    private Reservation toDomain(ReservationEntity e) {
        return new Reservation(e.getId(), e.getReservationNumber(), e.getStationId(), e.getChargerId(), e.getUserId(),
                e.getVehicleId(), e.getStartTime(), e.getExpirationTime(), e.getStatus(), e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
