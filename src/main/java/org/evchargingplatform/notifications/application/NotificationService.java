package org.evchargingplatform.notifications.application;

import org.evchargingplatform.notifications.application.port.in.GetNotificationsUseCase;
import org.evchargingplatform.notifications.application.port.in.NotifyUseCase;
import org.evchargingplatform.notifications.application.port.out.NotificationRepository;
import org.evchargingplatform.notifications.application.port.out.NotificationSender;
import org.evchargingplatform.notifications.domain.Notification;
import org.evchargingplatform.notifications.domain.NotificationType;
import org.evchargingplatform.notifications.domain.exception.NotificationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns domain events into driver notifications (FR-8): an inbox entry, plus delivery through
 * {@link NotificationSender}.
 * <p>
 * Built only from events, never by calling other modules, so it can't slow down or break charging, and
 * it would move to its own service unchanged. Idempotent on the source event id.
 */
@Service
@Transactional
public class NotificationService implements NotifyUseCase, GetNotificationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepository repository;
    private final NotificationSender sender;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationSender sender, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void reservationConfirmed(UUID eventId, UUID userId, UUID reservationId, Instant holdUntil) {
        notify(eventId, userId, NotificationType.RESERVATION_CONFIRMED, "Reservation confirmed",
                "Your connector is held for you until " + TIME.format(holdUntil) + ".", reservationId);
    }

    @Override
    public void reservationCancelled(UUID eventId, UUID userId, UUID reservationId) {
        notify(eventId, userId, NotificationType.RESERVATION_CANCELLED, "Reservation cancelled",
                "Your reservation was cancelled and the connector released.", reservationId);
    }

    @Override
    public void reservationExpired(UUID eventId, UUID userId, UUID reservationId) {
        notify(eventId, userId, NotificationType.RESERVATION_EXPIRED, "Reservation expired",
                "You didn't start charging in time, so the connector was released.", reservationId);
    }

    @Override
    public void chargingStarted(UUID eventId, UUID userId, UUID sessionId) {
        notify(eventId, userId, NotificationType.CHARGING_STARTED, "Charging started",
                "Your car is charging.", sessionId);
    }

    @Override
    public void chargingFinished(UUID eventId, UUID userId, UUID sessionId, BigDecimal energyKwh) {
        notify(eventId, userId, NotificationType.CHARGING_FINISHED, "Charging finished",
                energyKwh.stripTrailingZeros().toPlainString() + " kWh delivered.", sessionId);
    }

    @Override
    public void paymentReceived(UUID eventId, UUID userId, UUID paymentId, BigDecimal amountMinor, String currency) {
        notify(eventId, userId, NotificationType.PAYMENT_RECEIVED, "Payment received",
                money(amountMinor, currency) + " was charged to your card.", paymentId);
    }

    @Override
    public void paymentFailed(UUID eventId, UUID userId, UUID paymentId, String failureCode) {
        if ("card_declined".equals(failureCode)) {
            notify(eventId, userId, NotificationType.PAYMENT_DECLINED, "Card declined",
                    "Your card was declined, so charging didn't start. Try another card.", paymentId);
        } else {
            notify(eventId, userId, NotificationType.PAYMENT_FAILED, "Payment failed",
                    "We couldn't take payment for your charging session. Please update your card.", paymentId);
        }
    }

    @Override
    public void refundIssued(UUID eventId, UUID userId, UUID paymentId, BigDecimal amountMinor, String currency) {
        notify(eventId, userId, NotificationType.REFUND_ISSUED, "Refund issued",
                money(amountMinor, currency) + " is on its way back to your card.", paymentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findForUser(UUID userId, boolean unreadOnly) {
        return repository.findByUserId(userId, unreadOnly);
    }

    @Override
    @Transactional(readOnly = true)
    public Notification findById(UUID notificationId) {
        return repository.findById(notificationId).orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    @Override
    public Notification markRead(UUID notificationId) {
        return repository.save(findById(notificationId).markRead(clock));
    }

    private void notify(UUID eventId, UUID userId, NotificationType type, String title, String body, UUID subjectId) {
        if (userId == null) {
            // events published before Sprint 10 added userId to the payment contracts
            log.debug("Skipping {} for event {}: no user on the event", type, eventId);
            return;
        }
        if (repository.existsByEventId(eventId)) {
            log.debug("Event {} already notified", eventId);
            return;
        }
        Notification saved = repository.save(Notification.of(userId, eventId, type, title, body, subjectId, clock));
        sender.send(saved);
    }

    static String money(BigDecimal amountMinor, String currency) {
        BigDecimal major = amountMinor.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        try {
            return Currency.getInstance(currency).getSymbol(Locale.UK) + major.toPlainString();
        } catch (IllegalArgumentException | NullPointerException e) {
            return major.toPlainString() + " " + currency;
        }
    }
}
