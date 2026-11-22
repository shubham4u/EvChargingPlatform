package org.evchargingplatform.charging.adapter.out.messaging;

import org.evchargingplatform.charging.application.port.out.ChargingSessionEventPublisher;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.events.charging.v1.ChargingSessionRequestedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStartedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStoppedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link ChargingSessionEventPublisher} port.
 * <p>
 * Publishes immutable version-one charging session lifecycle events to Kafka.
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaChargingSessionEventPublisher implements ChargingSessionEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String chargingEventsTopic;

    public KafkaChargingSessionEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.charging-events-topic:charging.events.v1}") String chargingEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.chargingEventsTopic = chargingEventsTopic;
    }

    @Override
    public void sessionRequested(ChargingSession session) {
        ChargingSessionRequestedEvent event = new ChargingSessionRequestedEvent(
                UUID.randomUUID(), ChargingSessionRequestedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.reservationId());
        kafkaTemplate.send(chargingEventsTopic, session.id().toString(), event);
    }

    @Override
    public void sessionStarted(ChargingSession session) {
        ChargingSessionStartedEvent event = new ChargingSessionStartedEvent(
                UUID.randomUUID(), ChargingSessionStartedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.startedAt());
        kafkaTemplate.send(chargingEventsTopic, session.id().toString(), event);
    }

    @Override
    public void sessionStopped(ChargingSession session) {
        ChargingSessionStoppedEvent event = new ChargingSessionStoppedEvent(
                UUID.randomUUID(), ChargingSessionStoppedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.stoppedAt(), session.totalEnergyKwh());
        kafkaTemplate.send(chargingEventsTopic, session.id().toString(), event);
    }
}
