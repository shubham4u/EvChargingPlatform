package org.evchargingplatform.reservation.domain.exception;

public class ReservationAlreadyConfirmedException extends RuntimeException {
    public ReservationAlreadyConfirmedException() {
        super("Reservation is already confirmed");
    }
}
