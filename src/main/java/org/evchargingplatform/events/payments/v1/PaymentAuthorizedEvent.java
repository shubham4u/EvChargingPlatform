package org.evchargingplatform.events.payments.v1;

import org.evchargingplatform.events.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorizedEvent(
        UUID eventId,
        int eventVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID paymentIntentId,
        UUID sessionId,
        BigDecimal amountMinor,
        String currency,
        String providerReference
) implements DomainEvent {
    public static final int VERSION = 1;
}

