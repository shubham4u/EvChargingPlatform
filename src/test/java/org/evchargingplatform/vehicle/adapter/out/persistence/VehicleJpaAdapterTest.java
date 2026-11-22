package org.evchargingplatform.vehicle.adapter.out.persistence;

import org.evchargingplatform.vehicle.domain.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link VehicleJpaAdapter} against the real Flyway schema on H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import(VehicleJpaAdapter.class)
class VehicleJpaAdapterTest {

    @Autowired
    private VehicleJpaAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should find vehicles by owner and detect a registered VIN")
    void shouldFindByOwnerAndVin() {
        UUID owner = UUID.randomUUID();
        adapter.save(Vehicle.register(owner, "5YJ3E1EA7KF317000", "Tesla", "Model 3", Clock.systemUTC()));
        adapter.save(Vehicle.register(owner, "WVWZZZE1ZMP012345", "Volkswagen", "ID.3", Clock.systemUTC()));
        adapter.save(Vehicle.register(UUID.randomUUID(), "KMHK381GFMU123456", "Hyundai", "Kona", Clock.systemUTC()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, adapter.findByOwnerUserId(owner).size());
        assertTrue(adapter.existsByVin("5YJ3E1EA7KF317000"));
        assertFalse(adapter.existsByVin("1HGCM82633A004352"));
    }
}
