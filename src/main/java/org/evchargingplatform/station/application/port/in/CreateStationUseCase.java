package org.evchargingplatform.station.application.port.in;

import org.evchargingplatform.station.domain.Station;

public interface CreateStationUseCase {
    Station create(CreateStationCommand command);
}
