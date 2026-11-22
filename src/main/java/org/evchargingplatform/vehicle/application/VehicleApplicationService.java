package org.evchargingplatform.vehicle.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleCommand;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleUseCase;
import org.evchargingplatform.vehicle.application.port.out.VehicleEventPublisher;
import org.evchargingplatform.vehicle.application.port.out.VehicleRepository;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.evchargingplatform.vehicle.domain.exception.DuplicateVehicleException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotFoundException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotOwnedByUserException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing vehicle registration and lookup.
 * <p>
 * The owner is checked through the identity context's {@link GetUserUseCase} in-process, the
 * same single-deployable shortcut Charging takes with the reservation projection (Sprint 5
 * Decision 1). Calling the other context's inbound port keeps its persistence private.
 */
@Service
@Transactional
public class VehicleApplicationService implements RegisterVehicleUseCase, GetVehicleUseCase {

    private final VehicleRepository vehicleRepository;
    private final ObjectProvider<VehicleEventPublisher> eventPublisher;
    private final GetUserUseCase getUserUseCase;
    private final Clock clock;

    public VehicleApplicationService(VehicleRepository vehicleRepository,
                                     ObjectProvider<VehicleEventPublisher> eventPublisher,
                                     GetUserUseCase getUserUseCase,
                                     Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.getUserUseCase = getUserUseCase;
        this.clock = clock;
    }

    @Override
    public Vehicle register(RegisterVehicleCommand command) {
        getUserUseCase.findById(command.ownerUserId());
        Vehicle vehicle = Vehicle.register(command.ownerUserId(), command.vin(), command.make(), command.model(), clock);
        if (vehicleRepository.existsByVin(vehicle.vin())) {
            throw new DuplicateVehicleException(vehicle.vin());
        }
        Vehicle saved = vehicleRepository.save(vehicle);
        eventPublisher.ifAvailable(publisher -> publisher.vehicleRegistered(saved));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findByOwner(UUID ownerUserId) {
        getUserUseCase.findById(ownerUserId);
        return vehicleRepository.findByOwnerUserId(ownerUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Vehicle findById(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }

    @Override
    @Transactional(readOnly = true)
    public Vehicle findOwnedBy(UUID vehicleId, UUID userId) {
        Vehicle vehicle = findById(vehicleId);
        if (!vehicle.isOwnedBy(userId)) {
            throw new VehicleNotOwnedByUserException(vehicleId, userId);
        }
        return vehicle;
    }
}
