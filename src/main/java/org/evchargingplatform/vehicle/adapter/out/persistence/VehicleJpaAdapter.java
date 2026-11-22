package org.evchargingplatform.vehicle.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.vehicle.application.port.out.VehicleRepository;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.evchargingplatform.vehicle.infrastructure.persistence.VehicleEntity;
import org.evchargingplatform.vehicle.infrastructure.persistence.VehicleJpaRepository;
import org.springframework.stereotype.Component;

/**
 * JPA persistence adapter for the vehicle repository port.
 */
@Component
public class VehicleJpaAdapter implements VehicleRepository {

    private final VehicleJpaRepository repository;

    public VehicleJpaAdapter(VehicleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        return toDomain(repository.save(toEntity(vehicle)));
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Vehicle> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Vehicle> findByOwnerUserId(UUID ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByVin(String vin) {
        return repository.existsByVin(vin);
    }

    private VehicleEntity toEntity(Vehicle v) {
        return new VehicleEntity(v.id(), v.ownerUserId(), v.vin(), v.make(), v.model(), v.createdAt());
    }

    private Vehicle toDomain(VehicleEntity e) {
        return new Vehicle(e.id(), e.ownerUserId(), e.vin(), e.make(), e.model(), e.createdAt());
    }
}
