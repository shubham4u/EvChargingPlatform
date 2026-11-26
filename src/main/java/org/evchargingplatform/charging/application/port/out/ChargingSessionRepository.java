package org.evchargingplatform.charging.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.charging.domain.ChargingSession;

public interface ChargingSessionRepository {

    List<ChargingSession> findAll();

    List<ChargingSession> findByUserId(UUID userId);

    List<ChargingSession> findByStation(UUID stationId, Instant from, Instant to);

    Optional<ChargingSession> findById(UUID id);

    ChargingSession save(ChargingSession chargingSession);

    void delete(ChargingSession chargingSession);

    /** True if the connector has a session that is REQUESTED or ACTIVE. */
    boolean existsOpenSessionForConnector(UUID connectorId);

    /** True if the vehicle has a session that is REQUESTED or ACTIVE. */
    boolean existsOpenSessionForVehicle(UUID vehicleId);

}
