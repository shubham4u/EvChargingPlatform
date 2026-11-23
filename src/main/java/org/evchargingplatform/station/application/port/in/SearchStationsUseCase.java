package org.evchargingplatform.station.application.port.in;

import java.util.List;

public interface SearchStationsUseCase {
    /** Active stations matching the query: nearest first when a location is given, otherwise by name. */
    List<StationSearchResult> search(StationSearchQuery query);
}
