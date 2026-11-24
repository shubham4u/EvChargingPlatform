package org.evchargingplatform.payments.application.port.out;

import org.evchargingplatform.payments.domain.PaymentMethod;

import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository {
    PaymentMethod save(PaymentMethod method);

    Optional<PaymentMethod> findForUser(UUID userId);
}
