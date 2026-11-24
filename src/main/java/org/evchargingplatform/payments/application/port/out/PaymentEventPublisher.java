package org.evchargingplatform.payments.application.port.out;

import org.evchargingplatform.payments.domain.PaymentIntent;

public interface PaymentEventPublisher {

    void paymentAuthorized(PaymentIntent intent);

    void paymentCaptured(PaymentIntent intent);

    void paymentFailed(PaymentIntent intent);

    void paymentRefunded(PaymentIntent intent);
}
