package org.evchargingplatform.api.stations.dto;

import java.util.UUID;

public record StationResponse(
        UUID stationId,
        String externalRef,
        String name,
        String countryCode,
        double latitude,
        double longitude,
        String status
) {
}

