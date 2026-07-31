package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.events.DomainEvent;

/** Outbound port; the application layer is independent of Kafka. */
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
