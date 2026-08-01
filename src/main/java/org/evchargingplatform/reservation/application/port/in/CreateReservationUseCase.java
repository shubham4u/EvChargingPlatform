package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

/**
 * Use case for creating a new reservation.
 */
public interface CreateReservationUseCase {
    Reservation create(CreateReservationCommand command);
}