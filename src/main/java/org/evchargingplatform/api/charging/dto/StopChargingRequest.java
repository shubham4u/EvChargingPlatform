package org.evchargingplatform.api.charging.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StopChargingRequest(
        @NotNull UUID sessionId,
        BigDecimal meterReadingKwh
) {
}

