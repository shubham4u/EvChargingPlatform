package org.evchargingplatform.audit.application.port.in;

/**
 * Inbound port for raw events off Kafka. Idempotent: a redelivered event is stored once.
 */
public interface RecordAuditEventUseCase {
    void record(String topic, int partition, long offset, String key, String typeHeader, String traceparent,
            String payload);
}
