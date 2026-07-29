package org.evchargingplatform.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();

    int eventVersion();

    UUID correlationId();

    UUID causationId();

    Instant occurredAt();
}

