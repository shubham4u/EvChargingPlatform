package org.evchargingplatform.payments.application.port.in;

import org.evchargingplatform.payments.domain.PaymentIntent;

import java.util.UUID;

public interface RefundPaymentUseCase {
    /** Refunds a captured payment in full. */
    PaymentIntent refund(UUID paymentId);
}
