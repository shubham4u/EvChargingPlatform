package org.evchargingplatform.audit.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.evchargingplatform.audit.application.port.in.RecordAuditEventUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Copies every event from every {@code *.events.v1} topic into the audit store.
 * <p>
 * Unlike the other listeners, it reads the raw JSON (String deserializer) together with the record's
 * headers. The audit keeps the event exactly as published, and it needs the {@code __TypeId__} and
 * {@code traceparent} headers that the typed deserializer would hide. It has its own consumer group, so
 * auditing never shares partitions with the projections.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaAuditListener {

    private final RecordAuditEventUseCase audit;

    public KafkaAuditListener(RecordAuditEventUseCase audit) {
        this.audit = audit;
    }

    @KafkaListener(topicPattern = "${app.audit.topic-pattern:.*\\.events\\.v1}", groupId = "audit",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "metadata.max.age.ms=10000"   // pick up newly created topics within seconds
            })
    public void onEvent(ConsumerRecord<String, String> record) {
        audit.record(record.topic(), record.partition(), record.offset(), record.key(),
                header(record, "__TypeId__"), header(record, "traceparent"), record.value());
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
