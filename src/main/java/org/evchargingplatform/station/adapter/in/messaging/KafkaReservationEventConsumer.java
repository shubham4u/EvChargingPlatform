package org.evchargingplatform.station.adapter.in.messaging;

import org.evchargingplatform.events.reservations.v1.ReservationCancelledEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCompletedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCreatedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationExpiredEvent;
import org.evchargingplatform.station.application.port.in.ReservationEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer adapter for reservation lifecycle events.
 * <p>
 * Listens to the {@code reservation.events.v1} topic and delegates
 * to the {@link ReservationEventConsumer} application port.
 * <p>
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaReservationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaReservationEventConsumer.class);

    private final ReservationEventConsumer consumer;

    public KafkaReservationEventConsumer(ReservationEventConsumer consumer) {
        this.consumer = consumer;
    }

    @KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}",
                   groupId = "${app.messaging.consumer-group:station-service}")
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("Received ReservationCreatedEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationCreated(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId(),
                event.expiresAt());
    }

    @KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}",
                   groupId = "${app.messaging.consumer-group:station-service}")
    public void onReservationCancelled(ReservationCancelledEvent event) {
        log.info("Received ReservationCancelledEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationCancelled(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId());
    }

    @KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}",
                   groupId = "${app.messaging.consumer-group:station-service}")
    public void onReservationCompleted(ReservationCompletedEvent event) {
        log.info("Received ReservationCompletedEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationCompleted(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId());
    }

    @KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}",
                   groupId = "${app.messaging.consumer-group:station-service}")
    public void onReservationExpired(ReservationExpiredEvent event) {
        log.info("Received ReservationExpiredEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationExpired(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId());
    }
}