package org.evchargingplatform.reservation.domain.repository;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository port for reservation persistence.
 * <p>
 * Infrastructure adapters implement this port.
 * The application layer depends on this interface, not on any concrete technology.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    List<Reservation> findAll();

    List<Reservation> findExpired(Collection<ReservationStatus> statuses, Instant at);
}