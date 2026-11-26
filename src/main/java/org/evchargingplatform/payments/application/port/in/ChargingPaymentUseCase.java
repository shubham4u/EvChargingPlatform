package org.evchargingplatform.payments.application.port.in;

import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.exception.PaymentDeclinedException;
import org.evchargingplatform.payments.domain.exception.PaymentMethodRequiredException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The payment steps of a charging session, called in-process by Charging.
 */
public interface ChargingPaymentUseCase {

    /**
     * Places the pre-charge hold (amount set by {@code app.payments.hold-amount}) on the user's card.
     *
     * @throws PaymentMethodRequiredException if the user has no card
     * @throws PaymentDeclinedException       if the provider declines; the failure is still recorded
     */
    PaymentIntent authorizeHold(UUID sessionId, UUID userId, String currency);

    /**
     * Takes the session's final cost. Never throws for a provider problem: the returned intent is
     * {@code CAPTURED} or {@code FAILED}, and the caller decides what that means for the session.
     */
    PaymentIntent capture(UUID sessionId, BigDecimal amount);

    /**
     * Collects the amount owed for a session whose capture failed, with the card the driver has on file
     * now. Like {@link #capture}, it doesn't throw for a provider problem: the intent comes back
     * {@code CAPTURED} or still {@code FAILED}.
     *
     * @throws PaymentMethodRequiredException if the user has no card
     * @throws IllegalStateException          if the session's payment isn't FAILED
     */
    PaymentIntent collectOutstanding(UUID sessionId, BigDecimal amount);
}
