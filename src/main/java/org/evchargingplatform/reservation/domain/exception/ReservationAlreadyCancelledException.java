package org.evchargingplatform.reservation.domain.exception;

public class ReservationAlreadyCancelledException extends RuntimeException {
    public ReservationAlreadyCancelledException() {
        super("Reservation is already cancelled");
    }
}
