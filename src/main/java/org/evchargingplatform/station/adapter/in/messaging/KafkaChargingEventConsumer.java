package org.evchargingplatform.station.adapter.in.messaging;

import org.evchargingplatform.events.charging.v1.ChargingSessionStartedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStoppedEvent;
import org.evchargingplatform.station.application.port.in.ChargingEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer adapter for charging session events.
 * <p>
 * Listens to {@code charging.events.v1} and delegates to the {@link ChargingEventConsumer} port to keep
 * the connector occupancy read model current. Like {@link KafkaReservationEventConsumer}, it is one
 * class-level listener dispatching by payload type (see the Sprint 4 bug fix). The topic also carries
 * {@code ChargingSessionRequestedEvent}, which occupancy doesn't need, so a default handler acknowledges
 * any other type instead of failing on it.
 * <p>
 * Enabled only when {@code app.messaging.enabled=true}. Without Kafka the occupancy read model stays
 * empty, and search shows connectors as available.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@KafkaListener(topics = "${app.messaging.charging-events-topic:charging.events.v1}",
               groupId = "${app.messaging.consumer-group:station-service}")
public class KafkaChargingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaChargingEventConsumer.class);

    private final ChargingEventConsumer consumer;

    public KafkaChargingEventConsumer(ChargingEventConsumer consumer) {
        this.consumer = consumer;
    }

    @KafkaHandler
    public void onSessionStarted(ChargingSessionStartedEvent event) {
        log.info("Received ChargingSessionStartedEvent: eventId={}, sessionId={}", event.eventId(), event.sessionId());
        consumer.onSessionStarted(event.sessionId(), event.stationId(), event.connectorId(), event.startedAt());
    }

    @KafkaHandler
    public void onSessionStopped(ChargingSessionStoppedEvent event) {
        log.info("Received ChargingSessionStoppedEvent: eventId={}, sessionId={}", event.eventId(), event.sessionId());
        consumer.onSessionStopped(event.sessionId(), event.stationId(), event.connectorId(), event.stoppedAt());
    }

    @KafkaHandler(isDefault = true)
    public void onOtherEvent(Object event) {
        log.debug("Ignoring {} on the charging topic", event.getClass().getSimpleName());
    }
}
