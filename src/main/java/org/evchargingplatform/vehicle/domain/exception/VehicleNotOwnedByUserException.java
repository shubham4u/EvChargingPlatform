package org.evchargingplatform.vehicle.domain.exception;

import java.util.UUID;

public class VehicleNotOwnedByUserException extends RuntimeException {
    public VehicleNotOwnedByUserException(UUID vehicleId, UUID userId) {
        super("Vehicle " + vehicleId + " is not registered to user " + userId);
    }
}
