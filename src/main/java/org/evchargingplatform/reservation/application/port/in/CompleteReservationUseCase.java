package org.evchargingplatform.reservation.application.port.in;

import org.evchargingplatform.reservation.domain.Reservation;

import java.util.Optional;
import java.util.UUID;

/**
 * Use case for completing an active reservation.
 */
public interface CompleteReservationUseCase {
    Reservation complete(UUID reservationId);

    /**
     * Completes the reservation if it is ACTIVE, otherwise leaves it alone. Used when a charging session
     * ends: the reservation may already have been cancelled or expired, and that must not stop the session.
     */
    Optional<Reservation> completeIfActive(UUID reservationId);
}