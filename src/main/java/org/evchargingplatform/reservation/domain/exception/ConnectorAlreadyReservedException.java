package org.evchargingplatform.reservation.domain.exception;

public class ConnectorAlreadyReservedException extends RuntimeException {
    public ConnectorAlreadyReservedException() {
        super("Connector is already reserved");
    }
}
