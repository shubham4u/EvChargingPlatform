package org.evchargingplatform.payments.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PaymentIntent} state transitions.
 */
class PaymentIntentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final PaymentMethod CARD = new PaymentMethod(UUID.randomUUID(), "tok_visa", "visa", "4242", CLOCK.instant());

    private static PaymentIntent authorized() {
        return PaymentIntent.authorized(UUID.randomUUID(), CARD.userId(), "GBP", new BigDecimal("50.00"), CARD, "ref", CLOCK);
    }

    @Test
    @DisplayName("authorized -> captured -> refunded")
    void shouldFollowHappyPath() {
        PaymentIntent captured = authorized().captured(new BigDecimal("21.24"), CLOCK);
        assertEquals(PaymentStatus.CAPTURED, captured.status());
        assertEquals(new BigDecimal("21.24"), captured.capturedAmount());

        PaymentIntent refunded = captured.refunded(CLOCK);
        assertEquals(PaymentStatus.REFUNDED, refunded.status());
        assertEquals(new BigDecimal("21.24"), refunded.capturedAmount());
    }

    @Test
    @DisplayName("cannot capture more than the hold, or capture twice")
    void shouldGuardCapture() {
        assertThrows(IllegalStateException.class, () -> authorized().captured(new BigDecimal("50.01"), CLOCK));
        PaymentIntent captured = authorized().captured(BigDecimal.TEN, CLOCK);
        assertThrows(IllegalStateException.class, () -> captured.captured(BigDecimal.ONE, CLOCK));
    }

    @Test
    @DisplayName("a failed capture records why, and can't be refunded")
    void shouldRecordFailedCapture() {
        PaymentIntent failed = authorized().captureFailed("capture_failed", "issuer said no", CLOCK);

        assertEquals(PaymentStatus.FAILED, failed.status());
        assertEquals("capture_failed", failed.failureCode());
        assertThrows(IllegalStateException.class, () -> failed.refunded(CLOCK));
    }

    @Test
    @DisplayName("a failed capture can be recovered with another card, and only a FAILED one")
    void shouldRecover() {
        PaymentMethod newCard = new PaymentMethod(CARD.userId(), "tok_mastercard", "mastercard", "4444", CLOCK.instant());
        PaymentIntent recovered = authorized().captureFailed("capture_failed", "no", CLOCK)
                .recovered(newCard, "ref2", new BigDecimal("5.00"), CLOCK);

        assertEquals(PaymentStatus.CAPTURED, recovered.status());
        assertEquals("4444", recovered.cardLast4());
        assertNull(recovered.failureCode());
        assertThrows(IllegalStateException.class, () -> authorized().recovered(newCard, "r", BigDecimal.ONE, CLOCK));
    }

    @Test
    @DisplayName("a declined hold is FAILED from the start, with the reason")
    void shouldRecordDecline() {
        PaymentIntent declined = PaymentIntent.declined(UUID.randomUUID(), CARD.userId(), "GBP", new BigDecimal("50.00"),
                CARD, "card_declined", "declined", CLOCK);

        assertEquals(PaymentStatus.FAILED, declined.status());
        assertNull(declined.providerReference());
        assertEquals("4242", declined.cardLast4());
    }
}
