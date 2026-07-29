package org.evchargingplatform.station.application.port.out;

import org.evchargingplatform.station.domain.Station;

public interface StationEventPublisher {
    void stationRegistered(Station station);
}
