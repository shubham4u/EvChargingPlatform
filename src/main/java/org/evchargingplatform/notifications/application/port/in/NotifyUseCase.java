package org.evchargingplatform.notifications.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port for the events drivers are told about. Called by the Kafka adapters; every method is
 * idempotent on {@code eventId}.
 */
public interface NotifyUseCase {

    void reservationConfirmed(UUID eventId, UUID userId, UUID reservationId, Instant holdUntil);

    void reservationCancelled(UUID eventId, UUID userId, UUID reservationId);

    void reservationExpired(UUID eventId, UUID userId, UUID reservationId);

    void chargingStarted(UUID eventId, UUID userId, UUID sessionId);

    void chargingFinished(UUID eventId, UUID userId, UUID sessionId, BigDecimal energyKwh);

    void paymentReceived(UUID eventId, UUID userId, UUID paymentId, BigDecimal amountMinor, String currency);

    /** {@code failureCode} distinguishes a declined hold (no charging) from a failed final charge. */
    void paymentFailed(UUID eventId, UUID userId, UUID paymentId, String failureCode);

    void refundIssued(UUID eventId, UUID userId, UUID paymentId, BigDecimal amountMinor, String currency);
}
