package org.evchargingplatform.vehicle.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.evchargingplatform.vehicle.domain.Vehicle;

public interface VehicleRepository {

    Vehicle save(Vehicle vehicle);

    Optional<Vehicle> findById(UUID id);

    List<Vehicle> findAll();

    List<Vehicle> findByOwnerUserId(UUID ownerUserId);

    boolean existsByVin(String vin);
}
