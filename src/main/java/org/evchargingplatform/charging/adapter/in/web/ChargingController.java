package org.evchargingplatform.charging.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StopChargingSessionUseCase;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * REST adapter for charging sessions. The charging user is always the caller; stopping or
 * reading a session requires owning it or being an operator.
 */
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
            @NotNull UUID vehicleId,
            UUID reservationId) {   // no @NotNull — walk-up charging is valid
    }

    public record StopRequest(@NotNull BigDecimal totalEnergyKwh) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargingSessionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return ChargingSessionResponse.from(startChargingSessionUseCase.start(new StartChargingSessionCommand(
                request.stationId(),
                request.connectorId(),
                AuthenticatedUser.from(jwt).id(),
                request.vehicleId(),
                request.reservationId())));
    }

    @PatchMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.OK)
    public ChargingSessionResponse stop(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody StopRequest request) {
        owned(jwt, id);
        return ChargingSessionResponse.from(stopChargingSessionUseCase.stop(id, request.totalEnergyKwh()));
    }

    /** Pay what a {@code PAYMENT_FAILED} session owes, using the card now on file (owner or operator). */
    @PostMapping("/{id}/pay")
    public ChargingSessionResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        owned(jwt, id);
        return ChargingSessionResponse.from(stopChargingSessionUseCase.payOutstanding(id));
    }

    @GetMapping("/{id}")
    public ChargingSessionResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ChargingSessionResponse.from(owned(jwt, id));
    }

    @GetMapping
    public List<ChargingSessionResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        List<ChargingSession> sessions = caller.operator()
                ? getChargingSessionUseCase.getAllChargingSessions()
                : getChargingSessionUseCase.findByUser(caller.id());
        return sessions.stream()
                .map(ChargingSessionResponse::from)
                .toList();
    }

    private ChargingSession owned(Jwt jwt, UUID id) {
        ChargingSession session = getChargingSessionUseCase.findById(id);
        AuthenticatedUser.from(jwt).requireOwnerOrOperator(session.userId());
        return session;
    }
}
