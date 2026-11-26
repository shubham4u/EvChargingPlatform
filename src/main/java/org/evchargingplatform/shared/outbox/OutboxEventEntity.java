package org.evchargingplatform.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One event waiting to be (or already) published to Kafka. The identity id gives insertion order,
 * which the relay keeps, so a session's Requested is never sent after its Started.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 100)
    String topic;
    @Column(name = "event_key", length = 100)
    String key;
    @Column(name = "event_type", nullable = false)
    String eventType;
    @Column(nullable = false, length = 8000)
    String payload;
    @Column(name = "trace_id", length = 32)
    String traceId;
    @Column(name = "span_id", length = 16)
    String spanId;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "published_at")
    Instant publishedAt;
    @Column(nullable = false)
    int attempts;
    @Column(name = "last_error", length = 1000)
    String lastError;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(String topic, String key, String eventType, String payload, String traceId, String spanId,
            Instant createdAt) {
        this.topic = topic;
        this.key = key;
        this.eventType = eventType;
        this.payload = payload;
        this.traceId = traceId;
        this.spanId = spanId;
        this.createdAt = createdAt;
    }

    void published(Instant at) {
        this.publishedAt = at;
        this.attempts++;
        this.lastError = null;
    }

    void failed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    public Long id() { return id; }
    public String topic() { return topic; }
    public String key() { return key; }
    public String eventType() { return eventType; }
    public String payload() { return payload; }
    public String traceId() { return traceId; }
    public String spanId() { return spanId; }
    public Instant createdAt() { return createdAt; }
    public Instant publishedAt() { return publishedAt; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }
}
