package org.evchargingplatform.charging.adapter.out.messaging;

import org.evchargingplatform.charging.application.port.out.ChargingSessionEventPublisher;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.events.charging.v1.ChargingSessionRequestedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStartedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStoppedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.evchargingplatform.shared.outbox.EventOutbox;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link ChargingSessionEventPublisher} port.
 * <p>
 * Writes immutable version-one charging session lifecycle events to Kafka.
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaChargingSessionEventPublisher implements ChargingSessionEventPublisher {

    private final EventOutbox outbox;
    private final String chargingEventsTopic;

    public KafkaChargingSessionEventPublisher(
            EventOutbox outbox,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.charging-events-topic:charging.events.v1}") String chargingEventsTopic) {
        this.outbox = outbox;
        this.chargingEventsTopic = chargingEventsTopic;
    }

    @Override
    public void sessionRequested(ChargingSession session) {
        ChargingSessionRequestedEvent event = new ChargingSessionRequestedEvent(
                UUID.randomUUID(), ChargingSessionRequestedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.reservationId());
        outbox.send(chargingEventsTopic, session.id().toString(), event);
    }

    @Override
    public void sessionStarted(ChargingSession session) {
        ChargingSessionStartedEvent event = new ChargingSessionStartedEvent(
                UUID.randomUUID(), ChargingSessionStartedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.startedAt());
        outbox.send(chargingEventsTopic, session.id().toString(), event);
    }

    @Override
    public void sessionStopped(ChargingSession session) {
        ChargingSessionStoppedEvent event = new ChargingSessionStoppedEvent(
                UUID.randomUUID(), ChargingSessionStoppedEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                session.id(), session.userId(), session.stationId(), session.connectorId(),
                session.stoppedAt(), session.totalEnergyKwh());
        outbox.send(chargingEventsTopic, session.id().toString(), event);
    }
}
