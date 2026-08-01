package org.evchargingplatform.reservation.application.port.out;

import org.evchargingplatform.reservation.domain.Reservation;

/**
 * Outbound port for publishing reservation lifecycle events.
 * <p>
 * Infrastructure adapters (e.g. Kafka) implement this port.
 * The application layer remains unaware of the messaging technology.
 */
public interface ReservationEventPublisher {

    void reservationCreated(Reservation reservation);

    void reservationCancelled(Reservation reservation);

    void reservationCompleted(Reservation reservation);

    void reservationExpired(Reservation reservation);
}