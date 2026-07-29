package org.evchargingplatform.events.users.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record UserProfileUpdatedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID userId,
        String fullName,
        String phoneNumber,
        String locale
) implements DomainEvent {
    public static final int VERSION = 1;
}

