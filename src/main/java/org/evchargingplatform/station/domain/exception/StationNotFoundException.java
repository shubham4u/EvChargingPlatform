package org.evchargingplatform.station.domain.exception;

import java.util.UUID;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(UUID stationId) {
        super("Station not found: " + stationId);
    }
}
