package org.evchargingplatform.reservation.domain.exception;

public class ReservationAlreadyExpiredException extends RuntimeException {
    public ReservationAlreadyExpiredException() {
        super("Reservation is already expired");
    }
}
