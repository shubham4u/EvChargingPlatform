package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

import java.util.List;
import java.util.UUID;

/**
 * Use case for querying reservations.
 */
public interface GetReservationUseCase {
    List<Reservation> findAll();

    Reservation findById(UUID reservationId);
}