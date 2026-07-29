package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.Station;

import java.util.List;
import java.util.UUID;

public interface GetStationsUseCase {
    List<Station> findAll();

    Station findById(UUID stationId);
}
