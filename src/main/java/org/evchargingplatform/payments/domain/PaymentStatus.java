package org.evchargingplatform.payments.domain;

/**
 * {@code AUTHORIZED -> CAPTURED -> REFUNDED}, or {@code FAILED} when the provider declines the
 * authorization or the capture.
 */
public enum PaymentStatus {
    AUTHORIZED, CAPTURED, REFUNDED, FAILED
}
