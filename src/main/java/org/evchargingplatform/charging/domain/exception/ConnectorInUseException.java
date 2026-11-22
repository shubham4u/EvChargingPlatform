package org.evchargingplatform.charging.domain.exception;

public class ConnectorInUseException extends RuntimeException {
    public ConnectorInUseException(String message) {
        super(message);
    }
}
