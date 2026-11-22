package org.evchargingplatform.charging.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StopChargingSessionUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/charging/sessions")
public class ChargingController {
    
    private final StartChargingSessionUseCase startChargingSessionUseCase;
    private final StopChargingSessionUseCase stopChargingSessionUseCase;
    private final GetChargingSessionUseCase getChargingSessionUseCase;

    public ChargingController(
            StartChargingSessionUseCase startChargingSessionUseCase,
            StopChargingSessionUseCase stopChargingSessionUseCase,
            GetChargingSessionUseCase getChargingSessionUseCase) {
        this.startChargingSessionUseCase = startChargingSessionUseCase;
        this.stopChargingSessionUseCase = stopChargingSessionUseCase;
        this.getChargingSessionUseCase = getChargingSessionUseCase;
    }

    public record StartRequest(
            @NotNull UUID stationId,
            @NotNull UUID connectorId,
            @NotNull UUID userId,
            @NotNull UUID vehicleId,
            UUID reservationId) {   // no @NotNull — walk-up charging is valid
    }

    public record StopRequest(@NotNull BigDecimal totalEnergyKwh) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargingSessionResponse create(@Valid @RequestBody StartRequest request) {
        return ChargingSessionResponse.from(startChargingSessionUseCase.start(new StartChargingSessionCommand(
                request.stationId(),
                request.connectorId(),
                request.userId(),
                request.vehicleId(),
                request.reservationId())));
    }

    @PatchMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.OK)
    public ChargingSessionResponse stop(@PathVariable UUID id, @Valid @RequestBody StopRequest request) {
        return ChargingSessionResponse.from(stopChargingSessionUseCase.stop(id, request.totalEnergyKwh()));
    }

    @GetMapping("/{id}")
    public ChargingSessionResponse findById(@PathVariable UUID id) {
        return ChargingSessionResponse.from(getChargingSessionUseCase.findById(id));
    }

    @GetMapping
    public List<ChargingSessionResponse> findAll() {
        return getChargingSessionUseCase.getAllChargingSessions().stream()
                .map(ChargingSessionResponse::from)
                .toList();
    }
}
