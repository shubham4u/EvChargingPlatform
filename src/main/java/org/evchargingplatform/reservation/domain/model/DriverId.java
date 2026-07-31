package org.evchargingplatform.reservation.domain.model;

import java.util.UUID;

public record DriverId(UUID value) {
    public DriverId {
        if (value == null)
            throw new IllegalArgumentException("Driver id is required");
    }
}
