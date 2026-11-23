package org.evchargingplatform.notifications.adapter.in.web;

import org.evchargingplatform.notifications.application.port.in.GetNotificationsUseCase;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter for the caller's notification inbox.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final GetNotificationsUseCase notifications;

    public NotificationController(GetNotificationsUseCase notifications) {
        this.notifications = notifications;
    }

    public record NotificationResponse(UUID id, String type, String title, String body, UUID subjectId,
                                       Instant createdAt, Instant readAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id(), n.type().name(), n.title(), n.body(), n.subjectId(), n.createdAt(), n.readAt());
        }
    }

    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "false") boolean unread) {
        return notifications.findForUser(AuthenticatedUser.from(jwt).id(), unread).stream()
                .map(NotificationResponse::from).toList();
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        // Only the recipient; operators have no reason to read drivers' messages.
        Notification notification = notifications.findById(id);
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        if (!caller.id().equals(notification.userId())) {
            throw new org.springframework.security.access.AccessDeniedException("You can only read your own notifications");
        }
        return NotificationResponse.from(notifications.markRead(id));
    }
}
