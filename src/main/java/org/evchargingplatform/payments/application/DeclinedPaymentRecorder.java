package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.domain.PaymentIntent;

/**
 * Saves a declined authorization so the record outlives the charging start that triggered it, which
 * rolls back. See {@link TransactionalDeclinedPaymentRecorder}.
 */
interface DeclinedPaymentRecorder {
    PaymentIntent record(PaymentIntent declined);
}
