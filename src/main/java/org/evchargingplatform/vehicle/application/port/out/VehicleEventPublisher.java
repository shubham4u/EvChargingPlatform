package org.evchargingplatform.vehicle.application.port.out;

import org.evchargingplatform.vehicle.domain.Vehicle;

public interface VehicleEventPublisher {

    void vehicleRegistered(Vehicle vehicle);
}
