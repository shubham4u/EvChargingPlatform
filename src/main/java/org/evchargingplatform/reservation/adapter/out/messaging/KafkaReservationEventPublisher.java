package org.evchargingplatform.reservation.adapter.out.messaging;

import org.evchargingplatform.events.reservations.v1.ReservationCancelledEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCompletedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCreatedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationExpiredEvent;
import org.evchargingplatform.reservation.application.port.out.ReservationEventPublisher;
import org.evchargingplatform.reservation.domain.Reservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link ReservationEventPublisher} port.
 * <p>
 * Publishes immutable version-one reservation lifecycle events to Kafka.
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaReservationEventPublisher implements ReservationEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String reservationTopic;

    public KafkaReservationEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.reservation-topic:reservation.events.v1}") String reservationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.reservationTopic = reservationTopic;
    }

    @Override
    public void reservationCreated(Reservation reservation) {
        ReservationCreatedEvent event = new ReservationCreatedEvent(
                UUID.randomUUID(), ReservationCreatedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                reservation.id(), reservation.userId(), reservation.stationId(), reservation.chargerId(),
                reservation.expirationTime());
        kafkaTemplate.send(reservationTopic, reservation.id().toString(), event);
    }

    @Override
    public void reservationCancelled(Reservation reservation) {
        ReservationCancelledEvent event = new ReservationCancelledEvent(
                UUID.randomUUID(), ReservationCancelledEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                reservation.id(), reservation.userId(), reservation.stationId(), reservation.chargerId());
        kafkaTemplate.send(reservationTopic, reservation.id().toString(), event);
    }

    @Override
    public void reservationCompleted(Reservation reservation) {
        ReservationCompletedEvent event = new ReservationCompletedEvent(
                UUID.randomUUID(), ReservationCompletedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                reservation.id(), reservation.userId(), reservation.stationId(), reservation.chargerId());
        kafkaTemplate.send(reservationTopic, reservation.id().toString(), event);
    }

    @Override
    public void reservationExpired(Reservation reservation) {
        ReservationExpiredEvent event = new ReservationExpiredEvent(
                UUID.randomUUID(), ReservationExpiredEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                reservation.id(), reservation.userId(), reservation.stationId(), reservation.chargerId());
        kafkaTemplate.send(reservationTopic, reservation.id().toString(), event);
    }
}