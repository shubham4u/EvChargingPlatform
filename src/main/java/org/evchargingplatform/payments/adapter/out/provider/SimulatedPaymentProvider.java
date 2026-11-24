package org.evchargingplatform.payments.adapter.out.provider;

import org.evchargingplatform.payments.application.port.out.PaymentProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Stand-in for a real payment provider, with Stripe-style test tokens so every outcome can be exercised:
 * <ul>
 *   <li>{@code tok_visa}, {@code tok_mastercard}: everything succeeds;</li>
 *   <li>{@code tok_chargeDeclined}: the hold is declined ({@code card_declined});</li>
 *   <li>{@code tok_captureFails}: the hold succeeds but the final capture fails ({@code capture_failed}).</li>
 * </ul>
 * Any other token is rejected as unknown. Replacing this with a real provider means implementing
 * {@link PaymentProvider} against its API; nothing else changes.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final Map<String, Card> TEST_CARDS = Map.of(
            "tok_visa", new Card("visa", "4242"),
            "tok_mastercard", new Card("mastercard", "4444"),
            "tok_chargeDeclined", new Card("visa", "0002"),
            "tok_captureFails", new Card("visa", "0341"));

    @Override
    public Card describe(String token) {
        Card card = token == null ? null : TEST_CARDS.get(token);
        if (card == null) {
            throw new IllegalArgumentException("Unknown card token; use one of " + String.join(", ", TEST_CARDS.keySet().stream().sorted().toList()));
        }
        return card;
    }

    @Override
    public Result authorize(String token, BigDecimal amount, String currency, String idempotencyKey) {
        if ("tok_chargeDeclined".equals(token)) {
            return Result.failed("card_declined", "The card was declined by the issuer");
        }
        // Deterministic per idempotency key, like a real provider returning the same authorization on retry.
        return Result.ok("sim_auth_" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString().substring(0, 18));
    }

    @Override
    public Result capture(String token, String providerReference, BigDecimal amount, String currency) {
        if ("tok_captureFails".equals(token)) {
            return Result.failed("capture_failed", "The issuer rejected the final charge");
        }
        return Result.ok(providerReference);
    }

    @Override
    public Result refund(String providerReference, BigDecimal amount, String currency) {
        return Result.ok(providerReference);
    }
}
