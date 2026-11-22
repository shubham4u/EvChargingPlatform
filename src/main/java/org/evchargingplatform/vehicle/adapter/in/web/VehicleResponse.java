package org.evchargingplatform.vehicle.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import org.evchargingplatform.vehicle.domain.Vehicle;

public record VehicleResponse(
        UUID id,
        UUID ownerUserId,
        String vin,
        String make,
        String model,
        Instant createdAt) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.id(), vehicle.ownerUserId(), vehicle.vin(), vehicle.make(),
                vehicle.model(), vehicle.createdAt());
    }
}
