package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.application.port.in.StationSearchResult;
import org.evchargingplatform.station.domain.Station;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param distanceKm        from the search point, or null when no location was given
 * @param availableConnectors how many of the listed connectors are AVAILABLE now
 * @param connectors        the connectors matching the search's connector filters (all of them if none)
 */
public record StationSearchResponse(UUID id, String externalReference, String name, String countryCode,
                                    BigDecimal latitude, BigDecimal longitude, String status, Double distanceKm,
                                    long availableConnectors, List<ConnectorResponse> connectors) {
    static StationSearchResponse from(StationSearchResult result) {
        Station station = result.station();
        return new StationSearchResponse(station.id(), station.externalReference(), station.name(), station.countryCode(),
                station.latitude(), station.longitude(), station.status().name(), result.distanceKm(),
                result.availableConnectors(), result.connectors().stream().map(ConnectorResponse::from).toList());
    }
}
