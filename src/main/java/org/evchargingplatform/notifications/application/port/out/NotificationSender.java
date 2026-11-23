package org.evchargingplatform.notifications.application.port.out;

import org.evchargingplatform.notifications.domain.Notification;

/**
 * Port to an outbound channel (email, SMS, push). The in-app inbox is always written; this is the extra
 * delivery. Implementations must not throw for a delivery failure: the notification is already stored.
 */
public interface NotificationSender {
    void send(Notification notification);
}
