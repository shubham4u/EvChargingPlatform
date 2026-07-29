package org.evchargingplatform.events.identity.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID userId,
        String email,
        String displayName,
        String role
) implements DomainEvent {
    public static final int VERSION = 1;
}

