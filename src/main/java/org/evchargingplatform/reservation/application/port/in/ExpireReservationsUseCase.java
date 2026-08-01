package org.evchargingplatform.reservation.application.port.in;

/**
 * Use case for expiring overdue reservations.
 * <p>
 * Invoked by the scheduled expiration job.
 */
public interface ExpireReservationsUseCase {
    void expireReservations();
}