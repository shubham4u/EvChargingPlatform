package org.evchargingplatform.payments.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The money side of one charging session: a hold placed when charging starts, then the real cost
 * captured when it stops, and possibly refunded later.
 * <p>
 * Every provider outcome, success or failure, is recorded on the intent, so support can always see
 * why a session wasn't paid.
 */
public record PaymentIntent(UUID id, UUID sessionId, UUID userId, PaymentStatus status, String currency,
        BigDecimal authorizedAmount, BigDecimal capturedAmount, String cardToken, String cardBrand, String cardLast4,
        String providerReference, String failureCode, String failureReason, Instant createdAt, Instant updatedAt)
        implements Serializable {

    public static PaymentIntent authorized(UUID sessionId, UUID userId, String currency, BigDecimal amount,
            PaymentMethod card, String providerReference, Clock clock) {
        Instant now = Instant.now(clock);
        return new PaymentIntent(UUID.randomUUID(), sessionId, userId, PaymentStatus.AUTHORIZED, currency, amount, null,
                card.token(), card.brand(), card.last4(), providerReference, null, null, now, now);
    }

    public static PaymentIntent declined(UUID sessionId, UUID userId, String currency, BigDecimal amount,
            PaymentMethod card, String failureCode, String failureReason, Clock clock) {
        Instant now = Instant.now(clock);
        return new PaymentIntent(UUID.randomUUID(), sessionId, userId, PaymentStatus.FAILED, currency, amount, null,
                card.token(), card.brand(), card.last4(), null, failureCode, failureReason, now, now);
    }

    public PaymentIntent captured(BigDecimal amount, Clock clock) {
        require(PaymentStatus.AUTHORIZED, "capture");
        if (amount.compareTo(authorizedAmount) > 0) {
            throw new IllegalStateException("Cannot capture more than was authorized");
        }
        return with(PaymentStatus.CAPTURED, amount, null, null, clock);
    }

    public PaymentIntent captureFailed(String code, String reason, Clock clock) {
        require(PaymentStatus.AUTHORIZED, "fail capture of");
        return with(PaymentStatus.FAILED, null, code, reason, clock);
    }

    public PaymentIntent refunded(Clock clock) {
        require(PaymentStatus.CAPTURED, "refund");
        return with(PaymentStatus.REFUNDED, capturedAmount, null, null, clock);
    }

    private void require(PaymentStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Can only " + action + " a payment that is " + expected + ", this one is " + status);
        }
    }

    private PaymentIntent with(PaymentStatus newStatus, BigDecimal captured, String code, String reason, Clock clock) {
        return new PaymentIntent(id, sessionId, userId, newStatus, currency, authorizedAmount, captured, cardToken,
                cardBrand, cardLast4, providerReference, code, reason, createdAt, Instant.now(clock));
    }
}
