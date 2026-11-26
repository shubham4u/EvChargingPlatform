package org.evchargingplatform.shared.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Sends to Kafka and waits for the acknowledgement, so the relay only marks a row published once the
 * broker has it. Enabled only when {@code app.messaging.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
class KafkaOutboxSender implements OutboxSender {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    KafkaOutboxSender(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, Object event) throws Exception {
        kafkaTemplate.send(topic, key, event).get(10, TimeUnit.SECONDS);
    }
}
