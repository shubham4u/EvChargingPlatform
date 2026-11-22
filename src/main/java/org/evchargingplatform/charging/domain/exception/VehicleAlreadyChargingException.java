package org.evchargingplatform.charging.domain.exception;

import java.util.UUID;

public class VehicleAlreadyChargingException extends RuntimeException {
    public VehicleAlreadyChargingException(UUID vehicleId) {
        super("Vehicle already has an active charging session: " + vehicleId);
    }
}
