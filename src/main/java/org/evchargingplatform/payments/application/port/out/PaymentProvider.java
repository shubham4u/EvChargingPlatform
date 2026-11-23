package org.evchargingplatform.payments.application.port.out;

import java.math.BigDecimal;

/**
 * Port to the external payment provider (Stripe, Adyen, ...). Amounts are in major units with two
 * decimals. Every call returns a {@link Result} instead of throwing, so a decline is data the platform
 * records rather than an error it might lose.
 */
public interface PaymentProvider {

    /**
     * @throws IllegalArgumentException if the provider doesn't recognise the token
     */
    Card describe(String token);

    /** Places a hold. {@code idempotencyKey} makes retries safe. */
    Result authorize(String token, BigDecimal amount, String currency, String idempotencyKey);

    Result capture(String token, String providerReference, BigDecimal amount, String currency);

    Result refund(String providerReference, BigDecimal amount, String currency);

    record Card(String brand, String last4) {
    }

    record Result(boolean success, String providerReference, String failureCode, String failureReason) {
        public static Result ok(String providerReference) {
            return new Result(true, providerReference, null, null);
        }

        public static Result failed(String code, String reason) {
            return new Result(false, null, code, reason);
        }
    }
}
