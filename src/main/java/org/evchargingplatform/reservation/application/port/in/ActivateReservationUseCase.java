package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

import java.util.UUID;

/**
 * Use case for activating a reservation when its driver starts charging against it.
 * An ACTIVE reservation no longer expires; it completes when the session stops.
 */
public interface ActivateReservationUseCase {
    Reservation activate(UUID reservationId);
}
