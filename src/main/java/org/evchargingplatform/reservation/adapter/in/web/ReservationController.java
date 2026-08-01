package org.evchargingplatform.reservation.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.evchargingplatform.reservation.application.port.in.CancelReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.in.CreateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.GetReservationUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter for the reservation bounded context.
 * <p>
 * Depends on use case interfaces, not on the concrete application service.
 * Returns response DTOs to keep the domain model decoupled from the API contract.
 */
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final CreateReservationUseCase createReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final CompleteReservationUseCase completeReservationUseCase;
    private final GetReservationUseCase getReservationUseCase;

    public ReservationController(
            CreateReservationUseCase createReservationUseCase,
            CancelReservationUseCase cancelReservationUseCase,
            CompleteReservationUseCase completeReservationUseCase,
            GetReservationUseCase getReservationUseCase) {
        this.createReservationUseCase = createReservationUseCase;
        this.cancelReservationUseCase = cancelReservationUseCase;
        this.completeReservationUseCase = completeReservationUseCase;
        this.getReservationUseCase = getReservationUseCase;
    }

    public record CreateRequest(
            @NotNull UUID stationId,
            @NotNull UUID chargerId,
            @NotNull UUID userId,
            @NotNull UUID vehicleId,
            @NotNull Instant startTime) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody CreateRequest request) {
        return ReservationResponse.from(createReservationUseCase.create(
                new CreateReservationCommand(
                        request.stationId(),
                        request.chargerId(),
                        request.userId(),
                        request.vehicleId(),
                        request.startTime())));
    }

    @GetMapping
    public List<ReservationResponse> findAll() {
        return getReservationUseCase.findAll().stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ReservationResponse findById(@PathVariable UUID id) {
        return ReservationResponse.from(getReservationUseCase.findById(id));
    }

    @DeleteMapping("/{id}")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return ReservationResponse.from(cancelReservationUseCase.cancel(id));
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancelPatch(@PathVariable UUID id) {
        return ReservationResponse.from(cancelReservationUseCase.cancel(id));
    }

    @PatchMapping("/{id}/complete")
    public ReservationResponse complete(@PathVariable UUID id) {
        return ReservationResponse.from(completeReservationUseCase.complete(id));
    }
}