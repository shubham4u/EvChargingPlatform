package org.evchargingplatform.notifications.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    UUID id;
    @Column(name = "user_id", nullable = false)
    UUID userId;
    @Column(name = "event_id", nullable = false, unique = true)
    UUID eventId;
    @Column(nullable = false, length = 40)
    String type;
    @Column(nullable = false)
    String title;
    @Column(nullable = false)
    String body;
    @Column(name = "subject_id")
    UUID subjectId;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "read_at")
    Instant readAt;

    public NotificationEntity() {
    }

    public NotificationEntity(UUID id, UUID userId, UUID eventId, String type, String title, String body, UUID subjectId,
            Instant createdAt, Instant readAt) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.subjectId = subjectId;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID eventId() { return eventId; }
    public String type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public UUID subjectId() { return subjectId; }
    public Instant createdAt() { return createdAt; }
    public Instant readAt() { return readAt; }
}
