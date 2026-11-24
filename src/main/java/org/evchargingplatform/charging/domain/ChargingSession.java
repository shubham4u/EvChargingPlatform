package org.evchargingplatform.charging.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Charging session aggregate: {@code REQUESTED -> ACTIVE -> STOPPED}, or {@code -> PAYMENT_FAILED}
 * when the final charge could not be captured.
 * <p>
 * Decision 3 (identifiers): foreign references are raw {@link UUID}s, not value objects. This
 * matches what the {@code Reservation} aggregate actually does (its {@code domain/model} ID
 * wrappers are unused). Wrapping IDs only in Charging would leave the two contexts inconsistent
 * without making either one safer. Introducing typed IDs should be a cross-module change.
 * <p>
 * Sprint 9: a session records the tariff in force when it started ({@link #tariffId}), so a price
 * change mid-charge can't change the bill, and the final {@link #totalCost} once priced.
 * {@code PAYMENT_FAILED} exists so an unpaid session is never reported as a normal stop.
 */
public record ChargingSession(UUID id, String chargingSessionNumber, UUID stationId, UUID connectorId, UUID userId,
        UUID vehicleId, UUID reservationId, ChargingSessionStatus status, Instant startedAt, Instant stoppedAt,
        BigDecimal totalEnergyKwh, UUID tariffId, BigDecimal totalCost, String currency,
        Instant createdAt, Instant updatedAt) implements Serializable {

    public static ChargingSession create(UUID stationId, UUID connectorId, UUID userId, UUID vehicleId,
            UUID reservationId, Clock clock) {
        Instant now = Instant.now(clock);
        return new ChargingSession(UUID.randomUUID(), "Charging" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                stationId, connectorId, userId, vehicleId, reservationId, ChargingSessionStatus.REQUESTED,
                null, null, null, null, null, null, now, now);
    }

    /** Locks in the tariff the session will be billed at. */
    public ChargingSession withTariff(UUID tariff) {
        if (status != ChargingSessionStatus.REQUESTED) {
            throw new IllegalStateException("The tariff can only be set before charging starts");
        }
        return new ChargingSession(id, chargingSessionNumber, stationId, connectorId, userId, vehicleId, reservationId,
                status, startedAt, stoppedAt, totalEnergyKwh, tariff, totalCost, currency, createdAt, updatedAt);
    }

    public ChargingSession start(Clock clock) {
        if (status != ChargingSessionStatus.REQUESTED) {
            throw new IllegalStateException("Charging session can only be started from REQUESTED status");
        }
        return copy(ChargingSessionStatus.ACTIVE, Instant.now(clock), stoppedAt, totalEnergyKwh, clock);
    }

    public ChargingSession stop(BigDecimal totalEnergyKwh, Clock clock) {
        if (status != ChargingSessionStatus.ACTIVE) {
            throw new IllegalStateException("Charging session can only be stopped from ACTIVE status");
        }
        return copy(ChargingSessionStatus.STOPPED, startedAt, Instant.now(clock), totalEnergyKwh, clock);
    }

    /** Records the final bill for a stopped session. */
    public ChargingSession priced(BigDecimal cost, String costCurrency) {
        if (status != ChargingSessionStatus.STOPPED) {
            throw new IllegalStateException("Only a stopped session can be priced");
        }
        return new ChargingSession(id, chargingSessionNumber, stationId, connectorId, userId, vehicleId, reservationId,
                status, startedAt, stoppedAt, totalEnergyKwh, tariffId, cost, costCurrency, createdAt, updatedAt);
    }

    /** The car has finished charging but the money could not be taken: flagged for follow-up, never a normal stop. */
    public ChargingSession paymentFailed(Clock clock) {
        if (status != ChargingSessionStatus.STOPPED) {
            throw new IllegalStateException("Only a stopped session can fail payment");
        }
        return copy(ChargingSessionStatus.PAYMENT_FAILED, startedAt, stoppedAt, totalEnergyKwh, clock);
    }

    private ChargingSession copy(ChargingSessionStatus newStatus, Instant newStartedAt, Instant newStoppedAt,
            BigDecimal newTotalEnergyKwh, Clock clock) {
        return new ChargingSession(id, chargingSessionNumber, stationId, connectorId, userId, vehicleId,
                reservationId, newStatus, newStartedAt, newStoppedAt, newTotalEnergyKwh, tariffId, totalCost, currency,
                createdAt, Instant.now(clock));
    }
}
