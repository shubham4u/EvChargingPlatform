package org.evchargingplatform.charging.application.port.in;

import java.util.UUID;

public record StartChargingSessionCommand(
        UUID stationId,
        UUID connectorId,
        UUID userId,
        UUID vehicleId,
        UUID reservationId // nullable — walk-up charging
) {
}