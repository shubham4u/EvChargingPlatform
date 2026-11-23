package org.evchargingplatform.notifications.application.port.out;

import org.evchargingplatform.notifications.domain.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    boolean existsByEventId(UUID eventId);

    List<Notification> findByUserId(UUID userId, boolean unreadOnly);
}
