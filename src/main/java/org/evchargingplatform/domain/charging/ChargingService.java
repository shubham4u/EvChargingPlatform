package org.evchargingplatform.domain.charging;

import org.evchargingplatform.api.charging.dto.ChargingSessionResponse;
import org.evchargingplatform.api.charging.dto.StartChargingRequest;
import org.evchargingplatform.api.charging.dto.StopChargingRequest;

import java.util.UUID;

public interface ChargingService {
    ChargingSessionResponse start(StartChargingRequest request);

    ChargingSessionResponse stop(StopChargingRequest request);

    ChargingSessionResponse getById(UUID sessionId);
}

