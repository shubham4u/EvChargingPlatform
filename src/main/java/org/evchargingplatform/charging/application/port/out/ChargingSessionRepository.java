package org.evchargingplatform.charging.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface ChargingSessionRepository {

    List<ChargingSession> findAll();

    Optional<ChargingSession> findById(UUID id);

    ChargingSession save(ChargingSession chargingSession);

    void delete(ChargingSession chargingSession);

}
