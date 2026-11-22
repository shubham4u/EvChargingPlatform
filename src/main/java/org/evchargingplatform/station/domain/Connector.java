package org.evchargingplatform.station.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * A physical charging connector at a station.
 * <p>
 * Connectors carry no live status: whether one is in use is derived by the Charging context
 * from its own sessions, and reservation holds come from the reservation projection. Live
 * occupancy belongs to the future Availability Service (see {@code microservices.md}).
 */
public record Connector(UUID id, UUID stationId, ConnectorType connectorType, int maxPowerKw, Instant createdAt)
        implements Serializable {

    public static Connector add(UUID stationId, ConnectorType connectorType, int maxPowerKw, Clock clock) {
        if (maxPowerKw <= 0) {
            throw new IllegalArgumentException("maxPowerKw must be positive");
        }
        return new Connector(UUID.randomUUID(), stationId, connectorType, maxPowerKw, Instant.now(clock));
    }
}
