package org.evchargingplatform.reservation.adapter.in.web;

import org.evchargingplatform.reservation.domain.Reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * REST API response representation of a reservation.
 * <p>
 * Keeps the domain model decoupled from the API contract.
 */
public record ReservationResponse(
        UUID id,
        String reservationNumber,
        UUID stationId,
        UUID chargerId,
        UUID userId,
        UUID vehicleId,
        Instant startTime,
        Instant expirationTime,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.reservationNumber(),
                reservation.stationId(),
                reservation.chargerId(),
                reservation.userId(),
                reservation.vehicleId(),
                reservation.startTime(),
                reservation.expirationTime(),
                reservation.status().name(),
                reservation.createdAt(),
                reservation.updatedAt());
    }
}