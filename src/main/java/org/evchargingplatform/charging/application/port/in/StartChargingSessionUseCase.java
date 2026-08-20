package org.evchargingplatform.charging.application.port.in;


import org.evchargingplatform.charging.domain.ChargingSession;

public interface StartChargingSessionUseCase {
    ChargingSession start(StartChargingSessionCommand command);
}
