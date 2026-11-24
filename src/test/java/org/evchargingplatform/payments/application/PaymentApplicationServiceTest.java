package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.adapter.out.provider.SimulatedPaymentProvider;
import org.evchargingplatform.payments.application.port.out.PaymentEventPublisher;
import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.application.port.out.PaymentMethodRepository;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.PaymentStatus;
import org.evchargingplatform.payments.domain.exception.PaymentDeclinedException;
import org.evchargingplatform.payments.domain.exception.PaymentMethodRequiredException;
import org.evchargingplatform.support.TestObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentApplicationService}, run against the real {@link SimulatedPaymentProvider}
 * so each test card's behaviour is exercised as well.
 */
@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final BigDecimal HOLD = new BigDecimal("50.00");

    @Mock
    private PaymentIntentRepository intents;
    @Mock
    private PaymentMethodRepository methods;
    @Mock
    private DeclinedPaymentRecorder declinedRecorder;
    @Mock
    private PaymentEventPublisher publisher;

    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(new SimulatedPaymentProvider(), intents, methods, declinedRecorder,
                TestObjectProvider.of(publisher), HOLD, CLOCK);
    }

    private PaymentMethod card(String token) {
        PaymentMethod card = new PaymentMethod(USER, token, "visa", "4242", CLOCK.instant());
        lenient().when(methods.findForUser(USER)).thenReturn(Optional.of(card));
        return card;
    }

    private PaymentIntent authorizedWith(String token) {
        PaymentIntent intent = PaymentIntent.authorized(SESSION, USER, "GBP", HOLD,
                new PaymentMethod(USER, token, "visa", "4242", CLOCK.instant()), "sim_auth", CLOCK);
        lenient().when(intents.findBySessionId(SESSION)).thenReturn(Optional.of(intent));
        lenient().when(intents.save(any(PaymentIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return intent;
    }

    @Test
    @DisplayName("saving a card keeps brand and last4 from the provider; an unknown token is rejected")
    void shouldSaveCard() {
        when(methods.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod saved = service.setPaymentMethod(USER, "tok_mastercard");

        assertEquals("mastercard", saved.brand());
        assertEquals("4444", saved.last4());
        assertThrows(IllegalArgumentException.class, () -> service.setPaymentMethod(USER, "4242424242424242"));
    }

    @Nested
    @DisplayName("hold at start")
    class Hold {

        @Test
        @DisplayName("places the configured hold and publishes PaymentAuthorized")
        void shouldAuthorize() {
            card("tok_visa");
            when(intents.save(any(PaymentIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentIntent intent = service.authorizeHold(SESSION, USER, "GBP");

            assertEquals(PaymentStatus.AUTHORIZED, intent.status());
            assertEquals(HOLD, intent.authorizedAmount());
            assertNotNull(intent.providerReference());
            verify(publisher).paymentAuthorized(intent);
            verifyNoInteractions(declinedRecorder);
        }

        @Test
        @DisplayName("refuses without a saved card")
        void shouldRequireCard() {
            when(methods.findForUser(USER)).thenReturn(Optional.empty());

            assertThrows(PaymentMethodRequiredException.class, () -> service.authorizeHold(SESSION, USER, "GBP"));
            verifyNoInteractions(intents, publisher);
        }

        @Test
        @DisplayName("a decline is recorded in its own transaction, published, and then thrown")
        void shouldRecordDeclineSeparately() {
            card("tok_chargeDeclined");
            when(declinedRecorder.record(any(PaymentIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentDeclinedException thrown = assertThrows(PaymentDeclinedException.class,
                    () -> service.authorizeHold(SESSION, USER, "GBP"));

            assertEquals("card_declined", thrown.failureCode());
            ArgumentCaptor<PaymentIntent> captor = ArgumentCaptor.forClass(PaymentIntent.class);
            verify(declinedRecorder).record(captor.capture());
            assertEquals(PaymentStatus.FAILED, captor.getValue().status());
            verify(publisher).paymentFailed(captor.getValue());
            verify(intents, never()).save(any());   // not in the transaction that is about to roll back
        }
    }

    @Nested
    @DisplayName("capture at stop")
    class Capture {

        @Test
        @DisplayName("captures the cost and publishes PaymentCaptured")
        void shouldCapture() {
            authorizedWith("tok_visa");

            PaymentIntent result = service.capture(SESSION, new BigDecimal("21.24"));

            assertEquals(PaymentStatus.CAPTURED, result.status());
            assertEquals(new BigDecimal("21.24"), result.capturedAmount());
            verify(publisher).paymentCaptured(result);
        }

        @Test
        @DisplayName("returns FAILED, without throwing, when the provider rejects the capture")
        void shouldReturnFailedCapture() {
            authorizedWith("tok_captureFails");

            PaymentIntent result = service.capture(SESSION, new BigDecimal("21.24"));

            assertEquals(PaymentStatus.FAILED, result.status());
            assertEquals("capture_failed", result.failureCode());
            verify(publisher).paymentFailed(result);
        }

        @Test
        @DisplayName("returns FAILED when the cost is more than the hold")
        void shouldFailWhenOverHold() {
            authorizedWith("tok_visa");

            PaymentIntent result = service.capture(SESSION, new BigDecimal("50.01"));

            assertEquals(PaymentStatus.FAILED, result.status());
            assertEquals("amount_exceeds_authorization", result.failureCode());
        }

        @Test
        @DisplayName("a repeated capture returns the settled payment instead of charging twice")
        void shouldBeIdempotent() {
            PaymentIntent captured = PaymentIntent.authorized(SESSION, USER, "GBP", HOLD,
                    new PaymentMethod(USER, "tok_visa", "visa", "4242", CLOCK.instant()), "sim_auth", CLOCK)
                    .captured(BigDecimal.TEN, CLOCK);
            when(intents.findBySessionId(SESSION)).thenReturn(Optional.of(captured));

            assertSame(captured, service.capture(SESSION, BigDecimal.TEN));
            verify(intents, never()).save(any());
            verifyNoInteractions(publisher);
        }
    }

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("refunds a captured payment and publishes PaymentRefunded")
        void shouldRefund() {
            PaymentIntent captured = PaymentIntent.authorized(SESSION, USER, "GBP", HOLD,
                    new PaymentMethod(USER, "tok_visa", "visa", "4242", CLOCK.instant()), "sim_auth", CLOCK)
                    .captured(BigDecimal.TEN, CLOCK);
            when(intents.findById(captured.id())).thenReturn(Optional.of(captured));
            when(intents.save(any(PaymentIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentIntent refunded = service.refund(captured.id());

            assertEquals(PaymentStatus.REFUNDED, refunded.status());
            verify(publisher).paymentRefunded(refunded);
        }

        @Test
        @DisplayName("refuses to refund a payment that was never captured")
        void shouldRefuseUncaptured() {
            PaymentIntent authorized = authorizedWith("tok_visa");
            when(intents.findById(authorized.id())).thenReturn(Optional.of(authorized));

            assertThrows(IllegalStateException.class, () -> service.refund(authorized.id()));
            verify(intents, never()).save(any());
        }
    }
}
