package org.evchargingplatform.api.stations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStationRequest(
        @NotBlank String externalRef,
        @NotBlank String name,
        @NotBlank String countryCode,
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}

