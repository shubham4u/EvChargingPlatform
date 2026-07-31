package org.evchargingplatform.reservation.domain.model;

import java.util.UUID;

public record ReservationId(UUID value) {
    public ReservationId {
        if (value == null)
            throw new IllegalArgumentException("Reservation id is required");
    }
}
