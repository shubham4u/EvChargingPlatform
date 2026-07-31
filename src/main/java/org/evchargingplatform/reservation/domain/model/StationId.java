package org.evchargingplatform.reservation.domain.model;

import java.util.UUID;

public record StationId(UUID value) {
    public StationId {
        if (value == null)
            throw new IllegalArgumentException("Station id is required");
    }
}
