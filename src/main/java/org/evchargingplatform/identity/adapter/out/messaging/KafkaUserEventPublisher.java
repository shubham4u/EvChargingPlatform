package org.evchargingplatform.identity.adapter.out.messaging;

import org.evchargingplatform.events.identity.v1.UserRegisteredEvent;
import org.evchargingplatform.identity.application.port.out.UserEventPublisher;
import org.evchargingplatform.identity.domain.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.evchargingplatform.shared.outbox.EventOutbox;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka adapter for the {@link UserEventPublisher} port.
 * <p>
 * Writes immutable version-one user lifecycle events to Kafka.
 * Enabled only when {@code app.messaging.enabled=true} so the service
 * starts cleanly without Kafka in development.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaUserEventPublisher implements UserEventPublisher {

    private final EventOutbox outbox;
    private final String userEventsTopic;

    public KafkaUserEventPublisher(
            EventOutbox outbox,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.user-events-topic:user.events.v1}") String userEventsTopic) {
        this.outbox = outbox;
        this.userEventsTopic = userEventsTopic;
    }

    @Override
    public void userRegistered(User user) {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(), UserRegisteredEvent.VERSION, UUID.randomUUID(), null, Instant.now(),
                user.id(), user.email(), user.displayName(), user.role().name());
        outbox.send(userEventsTopic, user.id().toString(), event);
    }
}
