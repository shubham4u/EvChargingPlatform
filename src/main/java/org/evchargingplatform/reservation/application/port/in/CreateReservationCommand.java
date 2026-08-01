package org.evchargingplatform.reservation.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Command for creating a new reservation.
 *
 * @param stationId  the station to reserve at
 * @param chargerId  the specific charger/connector
 * @param userId     the driver making the reservation
 * @param vehicleId  the vehicle to charge
 * @param startTime  when the charging session should begin
 */
public record CreateReservationCommand(
        UUID stationId,
        UUID chargerId,
        UUID userId,
        UUID vehicleId,
        Instant startTime) {
}