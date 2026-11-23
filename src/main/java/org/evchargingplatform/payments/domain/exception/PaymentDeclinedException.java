package org.evchargingplatform.payments.domain.exception;

public class PaymentDeclinedException extends RuntimeException {
    private final String failureCode;

    public PaymentDeclinedException(String failureCode, String reason) {
        super("Your card was declined: " + reason);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
