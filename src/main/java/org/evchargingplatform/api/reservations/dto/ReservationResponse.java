package org.evchargingplatform.api.reservations.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID stationId,
        UUID connectorId,
        UUID userId,
        String status,
        Instant expiresAt
) {
}

