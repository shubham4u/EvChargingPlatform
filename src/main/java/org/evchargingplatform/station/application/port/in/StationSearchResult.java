package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.ConnectorAvailability;
import org.evchargingplatform.station.domain.Station;

import java.util.List;

/**
 * @param distanceKm null when the search had no location
 * @param connectors the station's connectors that match the query's connector filters (all of them if none)
 */
public record StationSearchResult(Station station, Double distanceKm, List<ConnectorStatus> connectors) {

    public long availableConnectors() {
        return connectors.stream().filter(c -> c.availability() == ConnectorAvailability.AVAILABLE).count();
    }
}
