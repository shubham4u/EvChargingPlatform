package org.evchargingplatform.audit.adapter.out.persistence;

import org.evchargingplatform.audit.domain.AuditEvent;
import org.evchargingplatform.notifications.adapter.out.persistence.NotificationJpaAdapter;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.notifications.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for the audit store and notification inbox against the real Flyway schema
 * (V1–V12) on H2, including the audit search with every combination of optional filters.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import({AuditEventJpaAdapter.class, NotificationJpaAdapter.class})
class AuditAndNotificationPersistenceTest {

    @Autowired
    private AuditEventJpaAdapter audit;
    @Autowired
    private NotificationJpaAdapter notifications;
    @Autowired
    private TestEntityManager entityManager;

    private AuditEvent event(String key, String type, String trace, int secondsAfter) {
        Instant at = Instant.parse("2026-01-01T12:00:00Z").plusSeconds(secondsAfter);
        return audit.save(new AuditEvent(UUID.randomUUID(), "charging.events.v1", 0, secondsAfter, key, type, at, trace,
                "{}", at));
    }

    @Test
    @DisplayName("audit search filters by key, type and trace (each optional), in occurrence order, with a limit")
    void shouldSearchAudit() {
        event("s1", "ChargingSessionStoppedEvent", "t2", 30);
        event("s1", "ChargingSessionStartedEvent", "t1", 10);
        event("s1", "PaymentAuthorizedEvent", "t1", 5);
        event("s2", "ChargingSessionStartedEvent", "t3", 20);
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of("PaymentAuthorizedEvent", "ChargingSessionStartedEvent", "ChargingSessionStoppedEvent"),
                audit.find("s1", null, null, 10).stream().map(AuditEvent::eventType).toList());
        assertEquals(2, audit.find(null, "ChargingSessionStartedEvent", null, 10).size());
        assertEquals(2, audit.find(null, null, "t1", 10).size());
        assertEquals(1, audit.find("s1", null, "t1", 1).size());
        assertEquals(4, audit.find(null, null, null, 10).size());
    }

    @Test
    @DisplayName("inbox lists newest first, can show unread only, and knows which events it has seen")
    void shouldStoreInbox() {
        UUID user = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), java.time.ZoneOffset.UTC);
        Notification first = notifications.save(Notification.of(user, eventId, NotificationType.CHARGING_STARTED, "a", "b", null, clock));
        notifications.save(Notification.of(user, UUID.randomUUID(), NotificationType.CHARGING_FINISHED, "c", "d", null,
                Clock.offset(clock, java.time.Duration.ofMinutes(5))));
        notifications.save(first.markRead(clock));
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(NotificationType.CHARGING_FINISHED, NotificationType.CHARGING_STARTED),
                notifications.findByUserId(user, false).stream().map(Notification::type).toList());
        assertEquals(1, notifications.findByUserId(user, true).size());
        assertTrue(notifications.existsByEventId(eventId));
        assertFalse(notifications.existsByEventId(UUID.randomUUID()));
    }
}
