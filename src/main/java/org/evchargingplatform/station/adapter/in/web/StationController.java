package org.evchargingplatform.station.adapter.in.web;

import jakarta.validation.Valid;
import org.evchargingplatform.station.application.port.in.CreateStationCommand;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
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
    private final CreateStationUseCase createStationUseCase;
    private final GetStationsUseCase getStationsUseCase;

    public StationController(CreateStationUseCase createStationUseCase, GetStationsUseCase getStationsUseCase) {
        this.createStationUseCase = createStationUseCase;
        this.getStationsUseCase = getStationsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StationResponse create(@Valid @RequestBody CreateStationRequest request) {
        return StationResponse.from(createStationUseCase.create(new CreateStationCommand(
                request.externalReference(), request.name(), request.countryCode(),
                request.latitude(), request.longitude())));
    }

    @GetMapping
    List<StationResponse> findAll() {
        return getStationsUseCase.findAll().stream().map(StationResponse::from).toList();
    }

    @GetMapping("/{id}")
    StationResponse findById(@PathVariable UUID id) {
        return StationResponse.from(getStationsUseCase.findById(id));
    }
}
