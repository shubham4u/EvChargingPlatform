package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a declined authorization in its own transaction. The charging start that triggered it rolls
 * back, but the record of the decline must survive for the driver's history and for support. It is a
 * separate bean because {@code REQUIRES_NEW} only applies through the Spring proxy, not on a self-call.
 */
@Component
class TransactionalDeclinedPaymentRecorder implements DeclinedPaymentRecorder {

    private final PaymentIntentRepository repository;

    TransactionalDeclinedPaymentRecorder(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIntent record(PaymentIntent declined) {
        return repository.save(declined);
    }
}
