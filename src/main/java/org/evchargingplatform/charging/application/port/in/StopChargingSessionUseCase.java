package org.evchargingplatform.charging.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface StopChargingSessionUseCase {
    ChargingSession stop(UUID sessionId, BigDecimal totalEnergyKwh);
}
