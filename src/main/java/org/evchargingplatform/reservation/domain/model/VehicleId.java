package org.evchargingplatform.reservation.domain.model;

import java.util.UUID;

public record VehicleId(UUID value) {
    public VehicleId {
        if (value == null)
            throw new IllegalArgumentException("Vehicle id is required");
    }
}
