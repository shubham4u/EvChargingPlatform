package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

import java.util.UUID;

/**
 * Use case for completing an active reservation.
 */
public interface CompleteReservationUseCase {
    Reservation complete(UUID reservationId);
}