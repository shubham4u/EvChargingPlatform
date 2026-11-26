package org.evchargingplatform.charging.adapter.out.persistence;

import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.exception.ConnectorInUseException;
import org.evchargingplatform.charging.domain.exception.VehicleAlreadyChargingException;
import org.evchargingplatform.charging.infrastructure.persistence.ChargingSessionJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The database backstop behind the "connector in use" / "vehicle already charging" checks (V14), as a lost
 * race would hit it: the application check passed, but another open session was inserted first. Runs on H2,
 * whose V14 builds the same rule from generated columns (Postgres uses partial unique indexes).
 * <p>
 * Each test runs without the usual rollback-only test transaction, so each save flushes and commits on its
 * own like separate requests, and cleans up after itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import(ChargingSessionJpaAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DoubleBookingGuardTest {

    private static final Clock CLOCK = Clock.systemUTC();

    @Autowired
    private ChargingSessionJpaAdapter adapter;

    @Autowired
    private ChargingSessionJpaRepository repository;

    // These rows really commit; other persistence tests share the in-memory database and count sessions.
    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private ChargingSession open(UUID connectorId, UUID vehicleId) {
        return ChargingSession.create(UUID.randomUUID(), connectorId, UUID.randomUUID(), vehicleId, null, CLOCK).start(CLOCK);
    }

    @Test
    @DisplayName("a second open session on the same connector is refused as 'connector in use'")
    void shouldRefuseSecondSessionOnConnector() {
        UUID connector = UUID.randomUUID();
        adapter.save(open(connector, UUID.randomUUID()));

        assertThrows(ConnectorInUseException.class, () -> adapter.save(open(connector, UUID.randomUUID())));
    }

    @Test
    @DisplayName("a second open session for the same vehicle is refused as 'vehicle already charging'")
    void shouldRefuseSecondSessionForVehicle() {
        UUID vehicle = UUID.randomUUID();
        adapter.save(open(UUID.randomUUID(), vehicle));

        assertThrows(VehicleAlreadyChargingException.class, () -> adapter.save(open(UUID.randomUUID(), vehicle)));
    }

    @Test
    @DisplayName("once a session stops, the connector and vehicle can be used again")
    void shouldFreeOnStop() {
        UUID connector = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        ChargingSession first = adapter.save(open(connector, vehicle));
        adapter.save(first.stop(BigDecimal.ONE, CLOCK));

        assertDoesNotThrow(() -> adapter.save(open(connector, vehicle)));
    }
}
