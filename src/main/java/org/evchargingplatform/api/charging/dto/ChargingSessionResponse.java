package org.evchargingplatform.api.charging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargingSessionResponse(
        UUID sessionId,
        UUID stationId,
        UUID connectorId,
        UUID userId,
        String status,
        Instant startedAt,
        Instant stoppedAt,
        BigDecimal totalEnergyKwh
) {
}

