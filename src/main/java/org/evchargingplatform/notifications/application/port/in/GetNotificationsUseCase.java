package org.evchargingplatform.notifications.application.port.in;

import org.evchargingplatform.notifications.domain.Notification;

import java.util.List;
import java.util.UUID;

public interface GetNotificationsUseCase {
    /** Newest first. */
    List<Notification> findForUser(UUID userId, boolean unreadOnly);

    Notification findById(UUID notificationId);

    Notification markRead(UUID notificationId);
}
