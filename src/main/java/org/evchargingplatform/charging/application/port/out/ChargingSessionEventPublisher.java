package org.evchargingplatform.charging.application.port.out;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface ChargingSessionEventPublisher {

    void sessionRequested(ChargingSession chargingSession);

    void sessionStarted(ChargingSession chargingSession);

    void sessionStopped(ChargingSession chargingSession);

} 