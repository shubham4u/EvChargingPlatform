package org.evchargingplatform.domain.stations;

import org.evchargingplatform.api.stations.dto.CreateStationRequest;
import org.evchargingplatform.api.stations.dto.StationResponse;

import java.util.List;
import java.util.UUID;

public interface StationService {
    StationResponse create(CreateStationRequest request);

    List<StationResponse> findAll();

    StationResponse getById(UUID stationId);
}

