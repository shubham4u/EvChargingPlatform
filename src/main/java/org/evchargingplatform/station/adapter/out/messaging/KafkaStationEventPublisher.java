package org.evchargingplatform.station.adapter.out.messaging;

import org.evchargingplatform.events.stations.v1.StationRegisteredEvent;
import org.evchargingplatform.station.application.port.out.StationEventPublisher;
import org.evchargingplatform.station.domain.Station;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
class KafkaStationEventPublisher implements StationEventPublisher {
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String stationTopic;

    KafkaStationEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                               @org.springframework.beans.factory.annotation.Value("${app.messaging.station-topic}") String stationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.stationTopic = stationTopic;
    }

    @Override
    public void stationRegistered(Station station) {
        StationRegisteredEvent event = new StationRegisteredEvent(
                UUID.randomUUID(), StationRegisteredEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                station.id(), station.externalReference(), station.name(), station.countryCode());
        kafkaTemplate.send(stationTopic, station.id().toString(), event);
    }
}
