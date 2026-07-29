package org.evchargingplatform.station.domain;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record Station(
        UUID id,
        String externalReference,
        String name,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        StationStatus status,
        Instant createdAt) implements Serializable {

    public static Station register(
            String externalReference,
            String name,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude) {
        return new Station(
                UUID.randomUUID(), externalReference, name, countryCode, latitude, longitude,
                StationStatus.ACTIVE, Instant.now());
    }
}
