package org.evchargingplatform.payments.application.port.in;

import org.evchargingplatform.payments.domain.PaymentMethod;

import java.util.Optional;
import java.util.UUID;

public interface ManagePaymentMethodUseCase {
    /** Saves (or replaces) the user's card from a provider token. */
    PaymentMethod setPaymentMethod(UUID userId, String token);

    Optional<PaymentMethod> findPaymentMethod(UUID userId);
}
