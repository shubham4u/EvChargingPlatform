package org.evchargingplatform.api.stations;

import jakarta.validation.Valid;
import org.evchargingplatform.api.stations.dto.CreateStationRequest;
import org.evchargingplatform.api.stations.dto.StationResponse;
import org.evchargingplatform.domain.stations.StationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stations")
public class StationController {
    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StationResponse create(@Valid @RequestBody CreateStationRequest request) {
        return stationService.create(request);
    }

    @GetMapping
    public List<StationResponse> getAll() {
        return stationService.findAll();
    }

    @GetMapping("/{id}")
    public StationResponse getById(@PathVariable UUID id) {
        return stationService.getById(id);
    }
}

