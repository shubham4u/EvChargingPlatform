package org.evchargingplatform.vehicle.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Vehicle} aggregate.
 */
class VehicleTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String VIN = "5YJ3E1EA7KF317000";

    @Test
    @DisplayName("should register a vehicle with an upper-cased, trimmed VIN")
    void shouldRegisterVehicle() {
        Vehicle vehicle = Vehicle.register(OWNER_ID, " 5yj3e1ea7kf317000 ", " Tesla ", " Model 3 ", CLOCK);

        assertEquals(VIN, vehicle.vin());
        assertEquals("Tesla", vehicle.make());
        assertEquals("Model 3", vehicle.model());
        assertEquals(OWNER_ID, vehicle.ownerUserId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"5YJ3E1EA7KF31700", "5YJ3E1EA7KF3170000", "5YJ3E1EA7KF31700O", "IYJ3E1EA7KF317000", "5YJ3E1EA7KF31700-"})
    @DisplayName("should reject a VIN that is not 17 valid characters")
    void shouldRejectInvalidVin(String vin) {
        assertThrows(IllegalArgumentException.class, () -> Vehicle.register(OWNER_ID, vin, "Tesla", "Model 3", CLOCK));
    }

    @Test
    @DisplayName("should report ownership only for the owning user")
    void shouldReportOwnership() {
        Vehicle vehicle = Vehicle.register(OWNER_ID, VIN, "Tesla", "Model 3", CLOCK);

        assertTrue(vehicle.isOwnedBy(OWNER_ID));
        assertFalse(vehicle.isOwnedBy(UUID.randomUUID()));
    }
}
