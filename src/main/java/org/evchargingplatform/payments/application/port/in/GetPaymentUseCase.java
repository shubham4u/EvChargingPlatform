package org.evchargingplatform.payments.application.port.in;

import org.evchargingplatform.payments.domain.PaymentIntent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GetPaymentUseCase {
    List<PaymentIntent> findAll();

    List<PaymentIntent> findByUser(UUID userId);

    PaymentIntent findById(UUID paymentId);

    List<PaymentIntent> findBySessions(Collection<UUID> sessionIds);
}
