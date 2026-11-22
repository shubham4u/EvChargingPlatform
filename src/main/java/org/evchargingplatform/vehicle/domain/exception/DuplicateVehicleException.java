package org.evchargingplatform.vehicle.domain.exception;

public class DuplicateVehicleException extends RuntimeException {
    public DuplicateVehicleException(String vin) {
        super("Vehicle already registered with VIN: " + vin);
    }
}
