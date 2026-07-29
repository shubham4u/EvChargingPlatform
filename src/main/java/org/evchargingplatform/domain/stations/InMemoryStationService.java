package org.evchargingplatform.domain.stations;

import org.evchargingplatform.api.stations.dto.CreateStationRequest;
import org.evchargingplatform.api.stations.dto.StationResponse;
import org.evchargingplatform.domain.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryStationService implements StationService {
    private final ConcurrentHashMap<UUID, StationResponse> stations = new ConcurrentHashMap<>();

    @Override
    public StationResponse create(CreateStationRequest request) {
        UUID stationId = UUID.randomUUID();
        StationResponse station = new StationResponse(
                stationId,
                request.externalRef(),
                request.name(),
                request.countryCode(),
                request.latitude(),
                request.longitude(),
                "ACTIVE"
        );
        stations.put(stationId, station);
        return station;
    }

    @Override
    public List<StationResponse> findAll() {
        return stations.values().stream().toList();
    }

    @Override
    public StationResponse getById(UUID stationId) {
        StationResponse station = stations.get(stationId);
        if (station == null) {
            throw new NotFoundException("Station not found: " + stationId);
        }
        return station;
    }
}

