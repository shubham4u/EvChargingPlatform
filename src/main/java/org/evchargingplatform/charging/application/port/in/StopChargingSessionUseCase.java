package org.evchargingplatform.charging.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface StopChargingSessionUseCase {
    ChargingSession stop(UUID sessionId, BigDecimal totalEnergyKwh);

    /** Pays a {@code PAYMENT_FAILED} session with the driver's current card; it becomes {@code STOPPED}. */
    ChargingSession payOutstanding(UUID sessionId);
}
