package org.evchargingplatform.reservation.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.evchargingplatform.reservation.application.port.in.CancelReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.in.CreateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.GetReservationUseCase;
import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * The reserving user is always the caller; reading or changing a reservation requires
 * owning it or being an operator.
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
            @NotNull UUID vehicleId,
            @NotNull Instant startTime) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return ReservationResponse.from(createReservationUseCase.create(
                new CreateReservationCommand(
                        request.stationId(),
                        request.chargerId(),
                        AuthenticatedUser.from(jwt).id(),
                        request.vehicleId(),
                        request.startTime())));
    }

    @GetMapping
    public List<ReservationResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        List<Reservation> reservations = caller.operator()
                ? getReservationUseCase.findAll()
                : getReservationUseCase.findByUser(caller.id());
        return reservations.stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ReservationResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ReservationResponse.from(owned(jwt, id));
    }

    @DeleteMapping("/{id}")
    public ReservationResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        owned(jwt, id);
        return ReservationResponse.from(cancelReservationUseCase.cancel(id));
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancelPatch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        owned(jwt, id);
        return ReservationResponse.from(cancelReservationUseCase.cancel(id));
    }

    @PatchMapping("/{id}/complete")
    public ReservationResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        owned(jwt, id);
        return ReservationResponse.from(completeReservationUseCase.complete(id));
    }

    private Reservation owned(Jwt jwt, UUID id) {
        Reservation reservation = getReservationUseCase.findById(id);
        AuthenticatedUser.from(jwt).requireOwnerOrOperator(reservation.userId());
        return reservation;
    }
}
