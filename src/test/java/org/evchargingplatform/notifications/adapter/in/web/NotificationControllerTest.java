package org.evchargingplatform.notifications.adapter.in.web;

import org.evchargingplatform.notifications.application.port.in.GetNotificationsUseCase;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.notifications.domain.NotificationType;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link NotificationController}.
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetNotificationsUseCase notifications;

    private final Notification n = Notification.of(USER, UUID.randomUUID(), NotificationType.PAYMENT_RECEIVED,
            "Payment received", "£19.74 was charged to your card.", UUID.randomUUID(), Clock.systemUTC());

    @Test
    @DisplayName("lists the caller's inbox, optionally unread only, in UTF-8")
    void shouldListInbox() throws Exception {
        when(notifications.findForUser(USER, true)).thenReturn(List.of(n));

        mockMvc.perform(get("/notifications").param("unread", "true").with(driver(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$[0].body").value("£19.74 was charged to your card."));
        verify(notifications).findForUser(USER, true);
    }

    @Test
    @DisplayName("401 without a token")
    void shouldRequireLogin() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only the recipient can mark a notification read, not even an operator")
    void shouldGuardMarkRead() throws Exception {
        when(notifications.findById(n.id())).thenReturn(n);
        when(notifications.markRead(n.id())).thenReturn(n.markRead(Clock.systemUTC()));

        mockMvc.perform(patch("/notifications/{id}/read", n.id()).with(driver(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
        mockMvc.perform(patch("/notifications/{id}/read", n.id()).with(operator(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(notifications, times(1)).markRead(any());
    }
}
