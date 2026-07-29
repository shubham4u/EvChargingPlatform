package org.evchargingplatform.station.domain.exception;

public class DuplicateStationException extends RuntimeException {
    public DuplicateStationException(String externalReference) {
        super("Station already exists with external reference: " + externalReference);
    }
}
