package org.evchargingplatform.events.payments.v1;

import org.evchargingplatform.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID paymentIntentId,
        UUID sessionId,
        String failureCode,
        String failureReason,
        UUID userId     // added in Sprint 10 so consumers needn't look the payment up
) implements DomainEvent {
    public static final int VERSION = 1;
}

