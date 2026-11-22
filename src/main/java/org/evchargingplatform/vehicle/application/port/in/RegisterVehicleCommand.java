package org.evchargingplatform.vehicle.application.port.in;

import java.util.UUID;

public record RegisterVehicleCommand(
        UUID ownerUserId,
        String vin,
        String make,
        String model) {
}
