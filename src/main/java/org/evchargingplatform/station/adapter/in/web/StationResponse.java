package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.domain.Station;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StationResponse(UUID id, String externalReference, String name, String countryCode,
                       BigDecimal latitude, BigDecimal longitude, String status, Instant createdAt) {
    static StationResponse from(Station station) {
        return new StationResponse(station.id(), station.externalReference(), station.name(),
                station.countryCode(), station.latitude(), station.longitude(),
                station.status().name(), station.createdAt());
    }
}
