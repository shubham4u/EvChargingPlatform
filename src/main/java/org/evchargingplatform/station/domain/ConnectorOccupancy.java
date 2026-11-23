package org.evchargingplatform.station.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model: a connector that currently has a charging session on it.
 * <p>
 * Maintained from {@code charging.events.v1} (started → row, stopped → no row), the same way
 * {@link ConnectorReservationProjection} is maintained from reservation events. It is the Station
 * Service's view of occupancy, so it can lag the Charging context by the Kafka delivery delay.
 */
public record ConnectorOccupancy(UUID connectorId, UUID stationId, UUID sessionId, Instant startedAt, Instant updatedAt)
        implements Serializable {
}
