package org.evchargingplatform.events.common;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt
) {
}

