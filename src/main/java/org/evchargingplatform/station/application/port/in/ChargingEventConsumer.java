package org.evchargingplatform.station.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for charging session events, which keep the connector occupancy read model current.
 * Called by the Kafka adapter; handlers must be idempotent because delivery is at-least-once.
 */
public interface ChargingEventConsumer {
    void onSessionStarted(UUID sessionId, UUID stationId, UUID connectorId, Instant startedAt);

    void onSessionStopped(UUID sessionId, UUID stationId, UUID connectorId, Instant stoppedAt);
}
