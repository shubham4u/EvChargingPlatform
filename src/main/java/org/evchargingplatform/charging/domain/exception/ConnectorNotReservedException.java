package org.evchargingplatform.charging.domain.exception;

public class ConnectorNotReservedException extends RuntimeException {
    public ConnectorNotReservedException(String string) {
        super(string);
    }
    
}
