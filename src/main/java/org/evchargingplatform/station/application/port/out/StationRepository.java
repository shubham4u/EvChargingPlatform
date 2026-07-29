package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.station.domain.Station;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StationRepository {
    Station save(Station station);

    List<Station> findAll();

    Optional<Station> findById(UUID stationId);

    boolean existsByExternalReference(String externalReference);
}
