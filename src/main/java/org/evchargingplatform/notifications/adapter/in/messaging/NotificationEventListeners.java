package org.evchargingplatform.notifications.adapter.in.messaging;

import org.evchargingplatform.events.charging.v1.ChargingSessionStartedEvent;
import org.evchargingplatform.events.charging.v1.ChargingSessionStoppedEvent;
import org.evchargingplatform.events.payments.v1.PaymentCapturedEvent;
import org.evchargingplatform.events.payments.v1.PaymentFailedEvent;
import org.evchargingplatform.events.payments.v1.PaymentRefundedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCancelledEvent;
import org.evchargingplatform.events.reservations.v1.ReservationCreatedEvent;
import org.evchargingplatform.events.reservations.v1.ReservationExpiredEvent;
import org.evchargingplatform.notifications.application.port.in.NotifyUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka adapters feeding {@link NotifyUseCase}, one listener per topic in their own consumer group
 * ({@code notifications}), so they see every event independently of the Station Service's projections.
 * Each is a single class-level listener dispatching by payload type (Sprint 4 lesson), with a default
 * handler for the event types drivers aren't told about.
 */
public final class NotificationEventListeners {

    private NotificationEventListeners() {
    }

    @Component
    @ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
    @KafkaListener(topics = "${app.messaging.reservation-topic:reservation.events.v1}", groupId = "notifications")
    public static class Reservations {
        private final NotifyUseCase notify;

        public Reservations(NotifyUseCase notify) {
            this.notify = notify;
        }

        @KafkaHandler
        public void on(ReservationCreatedEvent e) {
            notify.reservationConfirmed(e.eventId(), e.userId(), e.reservationId(), e.expiresAt());
        }

        @KafkaHandler
        public void on(ReservationCancelledEvent e) {
            notify.reservationCancelled(e.eventId(), e.userId(), e.reservationId());
        }

        @KafkaHandler
        public void on(ReservationExpiredEvent e) {
            notify.reservationExpired(e.eventId(), e.userId(), e.reservationId());
        }

        @KafkaHandler(isDefault = true)
        public void other(Object event) {
            // completed: the session-finished message already covers it
        }
    }

    @Component
    @ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
    @KafkaListener(topics = "${app.messaging.charging-events-topic:charging.events.v1}", groupId = "notifications")
    public static class Charging {
        private final NotifyUseCase notify;

        public Charging(NotifyUseCase notify) {
            this.notify = notify;
        }

        @KafkaHandler
        public void on(ChargingSessionStartedEvent e) {
            notify.chargingStarted(e.eventId(), e.userId(), e.sessionId());
        }

        @KafkaHandler
        public void on(ChargingSessionStoppedEvent e) {
            notify.chargingFinished(e.eventId(), e.userId(), e.sessionId(), e.totalEnergyKwh());
        }

        @KafkaHandler(isDefault = true)
        public void other(Object event) {
            // requested: started follows immediately
        }
    }

    @Component
    @ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
    @KafkaListener(topics = "${app.messaging.payment-events-topic:payment.events.v1}", groupId = "notifications")
    public static class Payments {
        private final NotifyUseCase notify;

        public Payments(NotifyUseCase notify) {
            this.notify = notify;
        }

        @KafkaHandler
        public void on(PaymentCapturedEvent e) {
            notify.paymentReceived(e.eventId(), e.userId(), e.paymentIntentId(), e.amountMinor(), e.currency());
        }

        @KafkaHandler
        public void on(PaymentFailedEvent e) {
            notify.paymentFailed(e.eventId(), e.userId(), e.paymentIntentId(), e.failureCode());
        }

        @KafkaHandler
        public void on(PaymentRefundedEvent e) {
            notify.refundIssued(e.eventId(), e.userId(), e.paymentIntentId(), e.amountMinor(), e.currency());
        }

        @KafkaHandler(isDefault = true)
        public void other(Object event) {
            // authorized: the hold isn't news to the driver
        }
    }
}
