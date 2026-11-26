package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.application.port.out.PaymentEventPublisher;
import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a declined authorization, and publishes its PaymentFailed event, in its own transaction. The
 * charging start that triggered it rolls back, but the decline must survive for the driver's history and
 * for support. Since Sprint 11 events go through the outbox in the same transaction as the change, so the
 * event has to be written here too: published from the caller, it would be rolled back with the start.
 * It is a separate bean because {@code REQUIRES_NEW} only applies through the Spring proxy, not on a self-call.
 */
@Component
class TransactionalDeclinedPaymentRecorder implements DeclinedPaymentRecorder {

    private final PaymentIntentRepository repository;
    private final ObjectProvider<PaymentEventPublisher> eventPublisher;

    TransactionalDeclinedPaymentRecorder(PaymentIntentRepository repository,
                                         ObjectProvider<PaymentEventPublisher> eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIntent record(PaymentIntent declined) {
        PaymentIntent saved = repository.save(declined);
        eventPublisher.ifAvailable(publisher -> publisher.paymentFailed(saved));
        return saved;
    }
}
