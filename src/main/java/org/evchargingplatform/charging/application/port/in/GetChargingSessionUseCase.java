package org.evchargingplatform.charging.application.port.in;

import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface GetChargingSessionUseCase {
    List<ChargingSession> getAllChargingSessions();

    ChargingSession findById(UUID chargingSessionId);

}
