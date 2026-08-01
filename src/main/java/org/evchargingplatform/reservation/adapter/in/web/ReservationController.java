package org.evchargingplatform.reservation.adapter.in.web;

import org.evchargingplatform.reservation.application.ReservationService;
import org.evchargingplatform.reservation.domain.Reservation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService s) {
        service = s;
    }

    public record CreateRequest(@NotNull UUID stationId, @NotNull UUID chargerId, @NotNull UUID userId, @NotNull UUID vehicleId, @NotNull Instant startTime) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation create(@Valid @RequestBody CreateRequest r) {
        return service.create(r.stationId(), r.chargerId(), r.userId(), r.vehicleId(), r.startTime());
    }

    @GetMapping
    public List<Reservation> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public Reservation get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public Reservation cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PatchMapping("/{id}/cancel")
    public Reservation cancelPatch(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PatchMapping("/{id}/complete")
    public Reservation complete(@PathVariable UUID id) {
        return service.complete(id);
    }
}
