package org.evchargingplatform.station.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for consuming reservation lifecycle events.
 * <p>
 * The Station Service consumes these events asynchronously from Kafka
 * to maintain connector availability projections without synchronous
 * REST coupling to the Reservation Service.
 */
public interface ReservationEventConsumer {

    void onReservationCreated(UUID reservationId, UUID stationId, UUID connectorId, UUID userId, Instant expiresAt);

    void onReservationCancelled(UUID reservationId, UUID stationId, UUID connectorId, UUID userId);

    void onReservationCompleted(UUID reservationId, UUID stationId, UUID connectorId, UUID userId);

    void onReservationExpired(UUID reservationId, UUID stationId, UUID connectorId, UUID userId);
}