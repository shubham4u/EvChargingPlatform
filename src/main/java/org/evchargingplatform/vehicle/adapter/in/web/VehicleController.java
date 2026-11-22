package org.evchargingplatform.vehicle.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleCommand;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleUseCase;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * REST adapter for the vehicle bounded context.
 * <p>
 * Vehicles are always registered to the caller. Drivers see only their own; operators can list
 * everyone's, optionally filtered by {@code ownerUserId}.
 */
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final RegisterVehicleUseCase registerVehicleUseCase;
    private final GetVehicleUseCase getVehicleUseCase;

    public VehicleController(RegisterVehicleUseCase registerVehicleUseCase, GetVehicleUseCase getVehicleUseCase) {
        this.registerVehicleUseCase = registerVehicleUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 17, max = 17) String vin,
            @NotBlank @Size(max = 50) String make,
            @NotBlank @Size(max = 50) String model) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest request) {
        return VehicleResponse.from(registerVehicleUseCase.register(new RegisterVehicleCommand(
                AuthenticatedUser.from(jwt).id(), request.vin(), request.make(), request.model())));
    }

    @GetMapping
    public List<VehicleResponse> findAll(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) UUID ownerUserId) {
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        List<Vehicle> vehicles;
        if (!caller.operator()) {
            caller.requireOwnerOrOperator(ownerUserId == null ? caller.id() : ownerUserId);
            vehicles = getVehicleUseCase.findByOwner(caller.id());
        } else {
            vehicles = ownerUserId == null ? getVehicleUseCase.findAll() : getVehicleUseCase.findByOwner(ownerUserId);
        }
        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public VehicleResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Vehicle vehicle = getVehicleUseCase.findById(id);
        AuthenticatedUser.from(jwt).requireOwnerOrOperator(vehicle.ownerUserId());
        return VehicleResponse.from(vehicle);
    }
}
