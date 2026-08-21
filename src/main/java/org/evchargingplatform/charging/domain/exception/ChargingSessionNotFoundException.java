package org.evchargingplatform.charging.domain.exception;

import java.util.UUID;

public class ChargingSessionNotFoundException extends RuntimeException {
    public ChargingSessionNotFoundException(UUID id) {
        super("Charging session not found: " + id);
    }
    
}
