package org.evchargingplatform.station.adapter.out.messaging;

import org.evchargingplatform.events.stations.v1.StationCreatedEvent;
import org.evchargingplatform.station.application.port.out.StationEventPublisher;
import org.evchargingplatform.station.domain.Station;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.evchargingplatform.events.DomainEvent;
import org.evchargingplatform.station.infrastructure.kafka.KafkaProperties;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
class KafkaStationEventPublisher implements StationEventPublisher {
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String stationTopic;

    KafkaStationEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                               KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
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
        kafkaTemplate.send(stationTopic, String.valueOf(event.aggregateId()), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).error("Kafka event publication failed eventId={} aggregateId={} topic={}", event.eventId(), event.aggregateId(), stationTopic, failure);
                    } else {
                        var metadata = result.getRecordMetadata();
                        org.slf4j.LoggerFactory.getLogger(getClass()).info("Kafka event published eventId={} aggregateId={} eventType={} topic={} partition={} offset={}", event.eventId(), event.aggregateId(), event.getClass().getSimpleName(), metadata.topic(), metadata.partition(), metadata.offset());
                    }
                });
    }
}
