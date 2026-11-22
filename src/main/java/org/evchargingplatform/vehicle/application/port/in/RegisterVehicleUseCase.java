package org.evchargingplatform.vehicle.application.port.in;

import org.evchargingplatform.vehicle.domain.Vehicle;

public interface RegisterVehicleUseCase {
    Vehicle register(RegisterVehicleCommand command);
}
