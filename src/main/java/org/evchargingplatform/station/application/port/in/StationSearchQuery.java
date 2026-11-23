package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.ConnectorType;

/**
 * Station search filters. Location is optional, but latitude and longitude must come together.
 * The connector filters apply per connector: a station matches when at least one of its connectors
 * satisfies all of them.
 *
 * @param availableOnly only connectors that are {@code AVAILABLE} right now
 */
public record StationSearchQuery(
        Double latitude,
        Double longitude,
        double radiusKm,
        ConnectorType connectorType,
        Integer minPowerKw,
        boolean availableOnly,
        int limit) {

    public static final double DEFAULT_RADIUS_KM = 10;
    public static final double MAX_RADIUS_KM = 200;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public StationSearchQuery {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("lat and lng must be given together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("lat must be -90..90 and lng -180..180");
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException("radiusKm must be greater than 0 and at most " + (int) MAX_RADIUS_KM);
        }
        if (minPowerKw != null && minPowerKw <= 0) {
            throw new IllegalArgumentException("minPowerKw must be positive");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT);
        }
    }

    public boolean hasLocation() {
        return latitude != null;
    }

    public boolean filtersConnectors() {
        return connectorType != null || minPowerKw != null || availableOnly;
    }
}
