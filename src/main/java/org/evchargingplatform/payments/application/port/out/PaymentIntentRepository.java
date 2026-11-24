package org.evchargingplatform.payments.application.port.out;

import org.evchargingplatform.payments.domain.PaymentIntent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository {
    PaymentIntent save(PaymentIntent intent);

    Optional<PaymentIntent> findById(UUID id);

    /** At most one per session: every start attempt has a fresh session id (unique in the table). */
    Optional<PaymentIntent> findBySessionId(UUID sessionId);

    List<PaymentIntent> findByUserId(UUID userId);

    List<PaymentIntent> findAll();
}
