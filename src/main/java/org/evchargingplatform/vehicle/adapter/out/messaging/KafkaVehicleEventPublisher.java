package org.evchargingplatform.vehicle.adapter.out.messaging;

import org.evchargingplatform.events.vehicles.v1.VehicleRegisteredEvent;
import org.evchargingplatform.vehicle.application.port.out.VehicleEventPublisher;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link VehicleEventPublisher} port.
 * <p>
 * Publishes immutable version-one vehicle lifecycle events to Kafka.
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaVehicleEventPublisher implements VehicleEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String vehicleEventsTopic;

    public KafkaVehicleEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.vehicle-events-topic:vehicle.events.v1}") String vehicleEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.vehicleEventsTopic = vehicleEventsTopic;
    }

    @Override
    public void vehicleRegistered(Vehicle vehicle) {
        VehicleRegisteredEvent event = new VehicleRegisteredEvent(
                UUID.randomUUID(), VehicleRegisteredEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                vehicle.id(), vehicle.ownerUserId(), vehicle.vin(), vehicle.make(), vehicle.model());
        kafkaTemplate.send(vehicleEventsTopic, vehicle.id().toString(), event);
    }
}
