package org.evchargingplatform.payments.domain.exception;

public class PaymentMethodRequiredException extends RuntimeException {
    public PaymentMethodRequiredException() {
        super("Add a card before charging: PUT /payments/methods/me");
    }
}
