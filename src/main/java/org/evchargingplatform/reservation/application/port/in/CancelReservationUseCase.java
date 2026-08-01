package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

import java.util.UUID;

/**
 * Use case for cancelling an existing reservation.
 */
public interface CancelReservationUseCase {
    Reservation cancel(UUID reservationId);
}