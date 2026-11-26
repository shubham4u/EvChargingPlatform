package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.domain.PaymentIntent;

/**
 * Saves a declined authorization, and announces it, so both outlive the charging start that triggered
 * it, which rolls back. See {@link TransactionalDeclinedPaymentRecorder}.
 */
interface DeclinedPaymentRecorder {
    PaymentIntent record(PaymentIntent declined);
}
