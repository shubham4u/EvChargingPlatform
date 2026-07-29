package org.evchargingplatform.api.reservations.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID stationId,
        @NotNull UUID connectorId,
        @NotNull UUID userId,
        @NotNull Instant expiresAt
) {
}

