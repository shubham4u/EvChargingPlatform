package org.evchargingplatform.reservation.domain;

import java.time.*;
import java.util.UUID;

public record Reservation(UUID id, String reservationNumber, UUID stationId, UUID chargerId,
        UUID userId, UUID vehicleId, Instant startTime, Instant expirationTime,
        ReservationStatus status, Instant createdAt, Instant updatedAt) {
    public static Reservation create(UUID stationId, UUID chargerId, UUID userId, UUID vehicleId, Instant start,
            Clock clock) {
        Instant now = Instant.now(clock);
        if (start.isBefore(now))
            throw new IllegalArgumentException("startTime must be in the future");
        return new Reservation(UUID.randomUUID(), "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                stationId, chargerId, userId, vehicleId, start, start.plus(Duration.ofMinutes(10)),
                ReservationStatus.CREATED, now, now);
    }

    public Reservation activate(Clock c) {
        ensureNotTerminal();
        return copy(ReservationStatus.ACTIVE, c);
    }

    public Reservation cancel(Clock c) {
        ensureNotTerminal();
        return copy(ReservationStatus.CANCELLED, c);
    }

    public Reservation complete(Clock c) {
        if (status != ReservationStatus.ACTIVE)
            throw new IllegalStateException("Only active reservations can complete");
        return copy(ReservationStatus.COMPLETED, c);
    }

    public Reservation expire(Clock c) {
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED
                || status == ReservationStatus.EXPIRED)
            return this;
        return copy(ReservationStatus.EXPIRED, c);
    }

    public boolean isExpired(Instant now) {
        return expirationTime.isBefore(now)
                && (status == ReservationStatus.CREATED || status == ReservationStatus.ACTIVE);
    }

    private void ensureNotTerminal() {
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED
                || status == ReservationStatus.EXPIRED)
            throw new IllegalStateException("Reservation is terminal");
    }

    private Reservation copy(ReservationStatus s, Clock c) {
        return new Reservation(id, reservationNumber, stationId, chargerId, userId, vehicleId, startTime,
                expirationTime, s, createdAt, Instant.now(c));
    }
}
