package org.evchargingplatform.charging.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface GetChargingSessionUseCase {
    List<ChargingSession> getAllChargingSessions();

    List<ChargingSession> findByUser(UUID userId);

    /** Sessions at the station created in {@code [from, to)}. */
    List<ChargingSession> findByStation(UUID stationId, Instant from, Instant to);

    ChargingSession findById(UUID chargingSessionId);

}
