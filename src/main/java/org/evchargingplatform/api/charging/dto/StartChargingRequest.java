package org.evchargingplatform.api.charging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartChargingRequest(
        @NotNull UUID stationId,
        @NotNull UUID connectorId,
        @NotNull UUID userId,
        UUID reservationId
) {
}

