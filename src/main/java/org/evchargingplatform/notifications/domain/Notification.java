package org.evchargingplatform.notifications.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * A message for one driver, created from one domain event.
 * <p>
 * {@link #eventId} is the source event's id and is unique, so a redelivered event can never notify
 * twice. {@link #subjectId} is what the message is about (session, reservation or payment), for linking.
 */
public record Notification(UUID id, UUID userId, UUID eventId, NotificationType type, String title, String body,
        UUID subjectId, Instant createdAt, Instant readAt) implements Serializable {

    public static Notification of(UUID userId, UUID eventId, NotificationType type, String title, String body,
            UUID subjectId, Clock clock) {
        return new Notification(UUID.randomUUID(), userId, eventId, type, title, body, subjectId, Instant.now(clock), null);
    }

    public Notification markRead(Clock clock) {
        return readAt != null ? this
                : new Notification(id, userId, eventId, type, title, body, subjectId, createdAt, Instant.now(clock));
    }
}
