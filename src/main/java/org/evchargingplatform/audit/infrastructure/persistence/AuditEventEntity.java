package org.evchargingplatform.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    @Column(name = "event_id")
    UUID eventId;
    @Column(nullable = false, length = 100)
    String topic;
    @Column(name = "kafka_partition", nullable = false)
    int partition;
    @Column(name = "kafka_offset", nullable = false)
    long offset;
    @Column(name = "event_key", length = 100)
    String key;
    @Column(name = "event_type", length = 100)
    String eventType;
    @Column(name = "occurred_at")
    Instant occurredAt;
    @Column(name = "trace_id", length = 32)
    String traceId;
    @Column(nullable = false, length = 8000)
    String payload;
    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    public AuditEventEntity() {
    }

    public AuditEventEntity(UUID eventId, String topic, int partition, long offset, String key, String eventType,
            Instant occurredAt, String traceId, String payload, Instant recordedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.traceId = traceId;
        this.payload = payload;
        this.recordedAt = recordedAt;
    }

    public UUID eventId() { return eventId; }
    public String topic() { return topic; }
    public int partition() { return partition; }
    public long offset() { return offset; }
    public String key() { return key; }
    public String eventType() { return eventType; }
    public Instant occurredAt() { return occurredAt; }
    public String traceId() { return traceId; }
    public String payload() { return payload; }
    public Instant recordedAt() { return recordedAt; }
}
