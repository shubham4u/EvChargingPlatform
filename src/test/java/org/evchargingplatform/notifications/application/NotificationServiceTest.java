package org.evchargingplatform.notifications.application;

import org.evchargingplatform.notifications.application.port.out.NotificationRepository;
import org.evchargingplatform.notifications.application.port.out.NotificationSender;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.notifications.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService}: the message for each event, idempotency, and delivery.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private NotificationRepository repository;
    @Mock
    private NotificationSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, sender, CLOCK);
        lenient().when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        verify(sender).send(captor.getValue());
        return captor.getValue();
    }

    @Test
    @DisplayName("payment received shows the amount from minor units with the currency symbol")
    void shouldNotifyPayment() {
        service.paymentReceived(UUID.randomUUID(), USER, UUID.randomUUID(), new BigDecimal("1974"), "GBP");

        Notification n = saved();
        assertEquals(NotificationType.PAYMENT_RECEIVED, n.type());
        assertEquals("£19.74 was charged to your card.", n.body());
        assertEquals(USER, n.userId());
    }

    @Test
    @DisplayName("a declined hold and a failed final charge get different messages")
    void shouldDistinguishDeclineFromFailedCapture() {
        service.paymentFailed(UUID.randomUUID(), USER, UUID.randomUUID(), "card_declined");
        service.paymentFailed(UUID.randomUUID(), USER, UUID.randomUUID(), "capture_failed");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(NotificationType.PAYMENT_DECLINED, captor.getAllValues().get(0).type());
        assertTrue(captor.getAllValues().get(0).body().contains("charging didn't start"));
        assertEquals(NotificationType.PAYMENT_FAILED, captor.getAllValues().get(1).type());
        assertTrue(captor.getAllValues().get(1).body().contains("update your card"));
    }

    @Test
    @DisplayName("reservation and charging messages")
    void shouldDescribeLifecycle() {
        service.reservationConfirmed(UUID.randomUUID(), USER, UUID.randomUUID(), Instant.parse("2026-01-01T12:10:00Z"));
        Notification confirmed = saved();
        assertEquals("Your connector is held for you until 12:10 UTC.", confirmed.body());

        reset(repository, sender);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.chargingFinished(UUID.randomUUID(), USER, UUID.randomUUID(), new BigDecimal("42.750"));
        assertEquals("42.75 kWh delivered.", saved().body());
    }

    @Test
    @DisplayName("a redelivered event is not notified twice")
    void shouldBeIdempotent() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        service.chargingStarted(eventId, USER, UUID.randomUUID());

        verify(repository, never()).save(any());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("events without a user (published before userId was added) are skipped")
    void shouldSkipWithoutUser() {
        service.refundIssued(UUID.randomUUID(), null, UUID.randomUUID(), new BigDecimal("500"), "EUR");

        verifyNoInteractions(repository, sender);
    }

    @Test
    @DisplayName("marking read stamps the time once")
    void shouldMarkRead() {
        Notification unread = Notification.of(USER, UUID.randomUUID(), NotificationType.CHARGING_STARTED, "t", "b", null, CLOCK);
        when(repository.findById(unread.id())).thenReturn(Optional.of(unread));

        Notification read = service.markRead(unread.id());

        assertEquals(CLOCK.instant(), read.readAt());
        assertSame(read, read.markRead(Clock.offset(CLOCK, java.time.Duration.ofHours(1))).readAt() == read.readAt() ? read : null);
    }

    @Test
    @DisplayName("money formatting falls back to the code for unknown currencies")
    void shouldFormatMoney() {
        assertEquals("€5.00", NotificationService.money(new BigDecimal("500"), "EUR"));
        assertEquals("5.00 XYZ", NotificationService.money(new BigDecimal("500"), "XYZ"));
    }
}
