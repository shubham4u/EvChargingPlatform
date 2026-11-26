package org.evchargingplatform.notifications.adapter.out.sender;

import org.evchargingplatform.notifications.application.port.out.NotificationSender;
import org.evchargingplatform.notifications.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for an email or push provider: logs what would be sent. Swapping in SES, SendGrid or FCM means
 * implementing {@link NotificationSender}; the inbox and the event handling don't change.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("NOTIFY user={} type={} title=\"{}\" body=\"{}\"", notification.userId(), notification.type(),
                notification.title(), notification.body());
    }
}
