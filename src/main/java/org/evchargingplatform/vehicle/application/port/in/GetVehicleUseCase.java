package org.evchargingplatform.vehicle.application.port.in;

import java.util.List;
import java.util.UUID;

import org.evchargingplatform.vehicle.domain.Vehicle;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotFoundException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotOwnedByUserException;

/**
 * Query port for vehicles, also called in-process by reservation and charging.
 */
public interface GetVehicleUseCase {
    List<Vehicle> findAll();

    List<Vehicle> findByOwner(UUID ownerUserId);

    Vehicle findById(UUID vehicleId);

    /**
     * Returns the vehicle if it is registered to {@code userId}.
     *
     * @throws VehicleNotFoundException       if the vehicle does not exist
     * @throws VehicleNotOwnedByUserException if it belongs to someone else
     */
    Vehicle findOwnedBy(UUID vehicleId, UUID userId);
}
