package org.evchargingplatform.payments.application;

import org.evchargingplatform.payments.application.port.out.PaymentEventPublisher;
import org.evchargingplatform.payments.application.port.out.PaymentIntentRepository;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.support.TestObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link TransactionalDeclinedPaymentRecorder}: the decline and its event are written together.
 */
@ExtendWith(MockitoExtension.class)
class TransactionalDeclinedPaymentRecorderTest {

    @Mock
    private PaymentIntentRepository repository;
    @Mock
    private PaymentEventPublisher publisher;

    @Test
    @DisplayName("saves the decline and publishes PaymentFailed in the same (separate) transaction")
    void shouldSaveAndPublish() {
        PaymentIntent declined = PaymentIntent.declined(UUID.randomUUID(), UUID.randomUUID(), "GBP", new BigDecimal("50.00"),
                new PaymentMethod(UUID.randomUUID(), "tok_chargeDeclined", "visa", "0002", Clock.systemUTC().instant()),
                "card_declined", "declined", Clock.systemUTC());
        when(repository.save(any(PaymentIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new TransactionalDeclinedPaymentRecorder(repository, TestObjectProvider.of(publisher)).record(declined);

        InOrder inOrder = inOrder(repository, publisher);
        inOrder.verify(repository).save(declined);
        inOrder.verify(publisher).paymentFailed(declined);
    }
}
