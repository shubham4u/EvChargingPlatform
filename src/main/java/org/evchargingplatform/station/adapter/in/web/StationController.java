package org.evchargingplatform.station.adapter.in.web;

import jakarta.validation.Valid;
import org.evchargingplatform.station.application.port.in.AddConnectorCommand;
import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.CreateStationCommand;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorAvailabilityUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.application.port.in.SearchStationsUseCase;
import org.evchargingplatform.station.application.port.in.StationSearchQuery;
import org.evchargingplatform.station.domain.ConnectorType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stations")
public class StationController {
    private final CreateStationUseCase createStationUseCase;
    private final GetStationsUseCase getStationsUseCase;
    private final AddConnectorUseCase addConnectorUseCase;
    private final GetConnectorAvailabilityUseCase connectorAvailabilityUseCase;
    private final SearchStationsUseCase searchStationsUseCase;

    public StationController(CreateStationUseCase createStationUseCase, GetStationsUseCase getStationsUseCase,
                             AddConnectorUseCase addConnectorUseCase,
                             GetConnectorAvailabilityUseCase connectorAvailabilityUseCase,
                             SearchStationsUseCase searchStationsUseCase) {
        this.createStationUseCase = createStationUseCase;
        this.getStationsUseCase = getStationsUseCase;
        this.addConnectorUseCase = addConnectorUseCase;
        this.connectorAvailabilityUseCase = connectorAvailabilityUseCase;
        this.searchStationsUseCase = searchStationsUseCase;
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

    /**
     * Find stations, e.g. {@code /stations/search?lat=51.53&lng=-0.12&radiusKm=5&connectorType=CCS2&minPowerKw=100&available=true}.
     * Every parameter is optional; {@code lat} and {@code lng} go together. Public, like the rest of station browsing.
     */
    @GetMapping("/search")
    List<StationSearchResponse> search(@RequestParam(required = false) Double lat,
                                       @RequestParam(required = false) Double lng,
                                       @RequestParam(defaultValue = "10") double radiusKm,
                                       @RequestParam(required = false) ConnectorType connectorType,
                                       @RequestParam(required = false) Integer minPowerKw,
                                       @RequestParam(defaultValue = "false") boolean available,
                                       @RequestParam(defaultValue = "20") int limit) {
        return searchStationsUseCase.search(new StationSearchQuery(lat, lng, radiusKm, connectorType, minPowerKw, available, limit))
                .stream().map(StationSearchResponse::from).toList();
    }

    @GetMapping("/{id}")
    StationResponse findById(@PathVariable UUID id) {
        return StationResponse.from(getStationsUseCase.findById(id));
    }

    @PostMapping("/{id}/connectors")
    @ResponseStatus(HttpStatus.CREATED)
    ConnectorResponse addConnector(@PathVariable UUID id, @Valid @RequestBody AddConnectorRequest request) {
        return ConnectorResponse.from(addConnectorUseCase.addConnector(
                new AddConnectorCommand(id, request.connectorType(), request.maxPowerKw())));
    }

    @GetMapping("/{id}/connectors")
    List<ConnectorResponse> findConnectors(@PathVariable UUID id) {
        return connectorAvailabilityUseCase.findByStation(id).stream().map(ConnectorResponse::from).toList();
    }
}
