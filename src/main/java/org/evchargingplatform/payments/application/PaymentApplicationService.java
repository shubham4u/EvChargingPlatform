package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.application.port.in.ChargingPaymentUseCase;
import org.evchargingplatform.payments.application.port.in.GetPaymentUseCase;
import org.evchargingplatform.payments.application.port.in.ManagePaymentMethodUseCase;
import org.evchargingplatform.payments.application.port.in.RefundPaymentUseCase;
import org.evchargingplatform.payments.application.port.out.PaymentEventPublisher;
import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.application.port.out.PaymentMethodRepository;
import org.evchargingplatform.payments.application.port.out.PaymentProvider;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.PaymentStatus;
import org.evchargingplatform.payments.domain.exception.PaymentDeclinedException;
import org.evchargingplatform.payments.domain.exception.PaymentMethodRequiredException;
import org.evchargingplatform.payments.domain.exception.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payments for charging sessions (FR-7): saved cards, the pre-charge hold, capture of the final cost,
 * and refunds, all through the {@link PaymentProvider} port.
 * <p>
 * Acceptance criterion 3 ("a payment failure does not silently complete a charging session") depends on
 * two rules here. A declined hold refuses the start. A failed capture is returned as a {@code FAILED}
 * intent, not swallowed, so Charging can mark the session {@code PAYMENT_FAILED}.
 */
@Service
@Transactional
public class PaymentApplicationService implements ManagePaymentMethodUseCase, ChargingPaymentUseCase,
        GetPaymentUseCase, RefundPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentProvider provider;
    private final PaymentIntentRepository intents;
    private final PaymentMethodRepository methods;
    private final DeclinedPaymentRecorder declinedRecorder;
    private final ObjectProvider<PaymentEventPublisher> eventPublisher;
    private final BigDecimal holdAmount;
    private final Clock clock;

    public PaymentApplicationService(PaymentProvider provider, PaymentIntentRepository intents,
                                     PaymentMethodRepository methods, DeclinedPaymentRecorder declinedRecorder,
                                     ObjectProvider<PaymentEventPublisher> eventPublisher,
                                     @Value("${app.payments.hold-amount:50.00}") BigDecimal holdAmount,
                                     Clock clock) {
        this.provider = provider;
        this.intents = intents;
        this.methods = methods;
        this.declinedRecorder = declinedRecorder;
        this.eventPublisher = eventPublisher;
        this.holdAmount = holdAmount;
        this.clock = clock;
    }

    @Override
    public PaymentMethod setPaymentMethod(UUID userId, String token) {
        PaymentProvider.Card card = provider.describe(token);
        return methods.save(new PaymentMethod(userId, token, card.brand(), card.last4(), Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethod> findPaymentMethod(UUID userId) {
        return methods.findForUser(userId);
    }

    @Override
    public PaymentIntent authorizeHold(UUID sessionId, UUID userId, String currency) {
        PaymentMethod card = methods.findForUser(userId).orElseThrow(PaymentMethodRequiredException::new);
        PaymentProvider.Result result = provider.authorize(card.token(), holdAmount, currency, sessionId.toString());
        if (!result.success()) {
            PaymentIntent declined = declinedRecorder.record(PaymentIntent.declined(sessionId, userId, currency, holdAmount,
                    card, result.failureCode(), result.failureReason(), clock));
            eventPublisher.ifAvailable(publisher -> publisher.paymentFailed(declined));
            log.info("Hold declined for session {}: {}", sessionId, result.failureCode());
            throw new PaymentDeclinedException(result.failureCode(), result.failureReason());
        }
        PaymentIntent saved = intents.save(PaymentIntent.authorized(sessionId, userId, currency, holdAmount, card,
                result.providerReference(), clock));
        eventPublisher.ifAvailable(publisher -> publisher.paymentAuthorized(saved));
        return saved;
    }

    @Override
    public PaymentIntent capture(UUID sessionId, BigDecimal amount) {
        PaymentIntent intent = intents.findBySessionId(sessionId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment for session " + sessionId));
        if (intent.status() != PaymentStatus.AUTHORIZED) {
            return intent;   // already settled; a retried stop must not charge twice
        }
        PaymentIntent outcome;
        if (amount.compareTo(intent.authorizedAmount()) > 0) {
            outcome = intent.captureFailed("amount_exceeds_authorization",
                    "Cost " + amount + " is more than the " + intent.authorizedAmount() + " hold", clock);
        } else {
            PaymentProvider.Result result = provider.capture(intent.cardToken(), intent.providerReference(), amount,
                    intent.currency());
            outcome = result.success()
                    ? intent.captured(amount, clock)
                    : intent.captureFailed(result.failureCode(), result.failureReason(), clock);
        }
        PaymentIntent saved = intents.save(outcome);
        if (saved.status() == PaymentStatus.CAPTURED) {
            eventPublisher.ifAvailable(publisher -> publisher.paymentCaptured(saved));
        } else {
            log.warn("Capture failed for session {}: {} ({})", sessionId, saved.failureCode(), saved.failureReason());
            eventPublisher.ifAvailable(publisher -> publisher.paymentFailed(saved));
        }
        return saved;
    }

    @Override
    public PaymentIntent refund(UUID paymentId) {
        PaymentIntent intent = findById(paymentId);
        PaymentIntent refunded = intent.refunded(clock);   // throws if not CAPTURED
        PaymentProvider.Result result = provider.refund(intent.providerReference(), intent.capturedAmount(), intent.currency());
        if (!result.success()) {
            throw new IllegalStateException("The provider refused the refund: " + result.failureReason());
        }
        PaymentIntent saved = intents.save(refunded);
        eventPublisher.ifAvailable(publisher -> publisher.paymentRefunded(saved));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentIntent> findAll() {
        return intents.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentIntent> findByUser(UUID userId) {
        return intents.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentIntent findById(UUID paymentId) {
        return intents.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }
}
