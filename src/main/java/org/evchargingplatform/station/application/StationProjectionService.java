package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.ReservationEventConsumer;
import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service that processes reservation lifecycle events
 * and maintains connector availability projections.
 * <p>
 * This service implements the {@link ReservationEventConsumer} port.
 * It is called by the Kafka consumer adapter when events arrive.
 * <p>
 * Idempotency: if a reservation event is received twice (e.g. due to
 * Kafka redelivery), the projection is updated to the same state without
 * producing duplicates or errors.
 */
@Service
@Transactional
public class StationProjectionService implements ReservationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StationProjectionService.class);

    private final ConnectorReservationProjectionRepository projectionRepository;

    public StationProjectionService(ConnectorReservationProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    @Override
    public void onReservationCreated(UUID reservationId, UUID stationId, UUID connectorId, UUID userId, Instant expiresAt) {
        log.info("Processing ReservationCreated: reservationId={}, stationId={}, connectorId={}",
                reservationId, stationId, connectorId);

        ConnectorReservationProjection projection = ConnectorReservationProjection.reserved(
                reservationId, stationId, connectorId, userId, expiresAt);
        projectionRepository.save(projection);

        log.info("Connector {} at station {} marked as RESERVED", connectorId, stationId);
    }

    @Override
    public void onReservationCancelled(UUID reservationId, UUID stationId, UUID connectorId, UUID userId) {
        log.info("Processing ReservationCancelled: reservationId={}, stationId={}, connectorId={}",
                reservationId, stationId, connectorId);
        releaseConnector(reservationId, stationId, connectorId);
    }

    @Override
    public void onReservationCompleted(UUID reservationId, UUID stationId, UUID connectorId, UUID userId) {
        log.info("Processing ReservationCompleted: reservationId={}, stationId={}, connectorId={}",
                reservationId, stationId, connectorId);
        releaseConnector(reservationId, stationId, connectorId);
    }

    @Override
    public void onReservationExpired(UUID reservationId, UUID stationId, UUID connectorId, UUID userId) {
        log.info("Processing ReservationExpired: reservationId={}, stationId={}, connectorId={}",
                reservationId, stationId, connectorId);
        releaseConnector(reservationId, stationId, connectorId);
    }

    private void releaseConnector(UUID reservationId, UUID stationId, UUID connectorId) {
        projectionRepository.findByReservationId(reservationId)
                .ifPresentOrElse(
                        projection -> {
                            ConnectorReservationProjection released = projection.released();
                            projectionRepository.save(released);
                            log.info("Connector {} at station {} marked as RELEASED", connectorId, stationId);
                        },
                        () -> log.warn("Reservation {} not found in projection — event may have been processed out of order or duplicated",
                                reservationId)
                );
    }
}