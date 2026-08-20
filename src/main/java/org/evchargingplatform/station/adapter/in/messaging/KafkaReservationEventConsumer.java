package org.evchargingplatform.station.adapter.in.messaging;

import org.evchargingplatform.events.reservations.v1.ReservationCancelledEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCompletedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCreatedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationExpiredEvent;
import org.evchargingplatform.station.application.port.in.ReservationEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer adapter for reservation lifecycle events.
 * <p>
 * Listens to the {@code reservation.events.v1} topic and delegates
 * to the {@link ReservationEventConsumer} application port.
 * <p>
 * A single listener instance handles all four event types via
 * {@link KafkaHandler} dispatch, based on the payload's deserialized
 * type. Using four independent {@code @KafkaListener} methods on the
 * same topic/group would register four separate consumers that split
 * the topic's partitions, so most events would arrive at a listener
 * whose method signature doesn't match their type and fail to convert.
 * <p>
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}",
               groupId = "${app.messaging.consumer-group:station-service}")
public class KafkaReservationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaReservationEventConsumer.class);

    private final ReservationEventConsumer consumer;

    public KafkaReservationEventConsumer(ReservationEventConsumer consumer) {
        this.consumer = consumer;
    }

    @KafkaHandler
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

    @KafkaHandler
    public void onReservationCancelled(ReservationCancelledEvent event) {
        log.info("Received ReservationCancelledEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationCancelled(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId());
    }

    @KafkaHandler
    public void onReservationCompleted(ReservationCompletedEvent event) {
        log.info("Received ReservationCompletedEvent: eventId={}, reservationId={}",
                event.eventId(), event.reservationId());
        consumer.onReservationCompleted(
                event.reservationId(),
                event.stationId(),
                event.connectorId(),
                event.userId());
    }

    @KafkaHandler
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