package org.evchargingplatform.station.adapter.out.messaging;

import org.evchargingplatform.events.stations.v1.StationCreatedEvent;
import org.evchargingplatform.station.application.port.out.StationEventPublisher;
import org.evchargingplatform.station.domain.Station;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.evchargingplatform.shared.outbox.EventOutbox;
import org.springframework.stereotype.Component;
import org.evchargingplatform.events.DomainEvent;
import org.evchargingplatform.station.infrastructure.kafka.KafkaProperties;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
class KafkaStationEventPublisher implements StationEventPublisher {
    private final EventOutbox outbox;
    private final String stationTopic;

    KafkaStationEventPublisher(EventOutbox outbox,
                               KafkaProperties properties) {
        this.outbox = outbox;
        this.stationTopic = properties.stationTopic();
    }

    @Override
    public void stationRegistered(Station station) {
        StationCreatedEvent event = new StationCreatedEvent(
                UUID.randomUUID(), StationCreatedEvent.VERSION, Instant.now(), UUID.randomUUID(), null,
                station.id(), station.externalReference(), station.name(), station.countryCode());
        publish(event);
    }

    @Override
    public void publish(DomainEvent event) {
        // Delivery (and any failure) is now logged by the outbox relay, after the transaction commits.
        outbox.send(stationTopic, String.valueOf(event.aggregateId()), event);
    }
}
