package org.evchargingplatform.payments.adapter.out.messaging;

import org.evchargingplatform.events.payments.v1.PaymentAuthorizedEvent;
import org.evchargingplatform.events.payments.v1.PaymentCapturedEvent;
import org.evchargingplatform.events.payments.v1.PaymentFailedEvent;
import org.evchargingplatform.events.payments.v1.PaymentRefundedEvent;
import org.evchargingplatform.payments.application.port.out.PaymentEventPublisher;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link PaymentEventPublisher} port.
 * <p>
 * Publishes immutable version-one payment events, keyed by session id so a session's payment events
 * stay in order. The event contracts carry amounts in minor units (pence, cents).
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String paymentEventsTopic;

    public KafkaPaymentEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.payment-events-topic:payment.events.v1}") String paymentEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Override
    public void paymentAuthorized(PaymentIntent p) {
        send(p, new PaymentAuthorizedEvent(UUID.randomUUID(), PaymentAuthorizedEvent.VERSION, UUID.randomUUID(), null,
                Instant.now(), p.id(), p.sessionId(), minor(p.authorizedAmount()), p.currency(), p.providerReference(), p.userId()));
    }

    @Override
    public void paymentCaptured(PaymentIntent p) {
        send(p, new PaymentCapturedEvent(UUID.randomUUID(), PaymentCapturedEvent.VERSION, UUID.randomUUID(), null,
                Instant.now(), p.id(), p.sessionId(), minor(p.capturedAmount()), p.currency(), p.providerReference(),
                p.userId()));
    }

    @Override
    public void paymentFailed(PaymentIntent p) {
        send(p, new PaymentFailedEvent(UUID.randomUUID(), PaymentFailedEvent.VERSION, UUID.randomUUID(), null,
                Instant.now(), p.id(), p.sessionId(), p.failureCode(), p.failureReason(), p.userId()));
    }

    @Override
    public void paymentRefunded(PaymentIntent p) {
        send(p, new PaymentRefundedEvent(UUID.randomUUID(), PaymentRefundedEvent.VERSION, UUID.randomUUID(), null,
                Instant.now(), p.id(), p.sessionId(), minor(p.capturedAmount()), p.currency(), p.providerReference(),
                p.userId()));
    }

    private void send(PaymentIntent p, Object event) {
        kafkaTemplate.send(paymentEventsTopic, p.sessionId().toString(), event);
    }

    private static BigDecimal minor(BigDecimal amount) {
        return amount.movePointRight(2);
    }
}
