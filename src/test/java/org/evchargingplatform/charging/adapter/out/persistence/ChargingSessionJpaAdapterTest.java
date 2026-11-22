package org.evchargingplatform.charging.adapter.out.persistence;

import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.evchargingplatform.charging.infrastructure.persistence.ChargingSessionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link ChargingSessionJpaAdapter}.
 * <p>
 * Runs against the real Flyway schema on H2. Flushing and clearing the persistence
 * context between saves simulates separate requests, which is what exposed the
 * {@code @Version} bug that mocked-repository tests could not see.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import(ChargingSessionJpaAdapter.class)
class ChargingSessionJpaAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private ChargingSessionJpaAdapter adapter;

    @Autowired
    private ChargingSessionJpaRepository jpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should persist a full lifecycle across separate persistence contexts")
    void shouldPersistFullLifecycle() {
        ChargingSession requested = ChargingSession.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, CLOCK);

        // start(): two saves in one transaction, as in ChargingSessionApplicationService
        ChargingSession saved = adapter.save(requested);
        adapter.save(saved.start(CLOCK));
        flushAndClear();

        // stop(): a later request
        ChargingSession active = adapter.findById(requested.id()).orElseThrow();
        adapter.save(active.stop(new BigDecimal("18.250"), CLOCK));
        flushAndClear();

        ChargingSession stopped = adapter.findById(requested.id()).orElseThrow();
        assertEquals(ChargingSessionStatus.STOPPED, stopped.status());
        assertEquals(0, new BigDecimal("18.250").compareTo(stopped.totalEnergyKwh()));
        assertEquals(CLOCK.instant(), stopped.startedAt());
        assertEquals(CLOCK.instant(), stopped.stoppedAt());
        assertEquals(2L, jpaRepository.findById(requested.id()).orElseThrow().version());
    }

    @Test
    @DisplayName("should round-trip an optional reservationId")
    void shouldRoundTripReservationId() {
        UUID reservationId = UUID.randomUUID();
        ChargingSession session = ChargingSession.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), reservationId, CLOCK);

        adapter.save(session);
        flushAndClear();

        assertEquals(reservationId, adapter.findById(session.id()).orElseThrow().reservationId());
        assertEquals(1, adapter.findAll().size());
    }

    @Test
    @DisplayName("should report open sessions per connector and vehicle until the session stops")
    void shouldReportOpenSessions() {
        UUID connectorId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        ChargingSession active = adapter.save(ChargingSession.create(
                UUID.randomUUID(), connectorId, UUID.randomUUID(), vehicleId, null, CLOCK).start(CLOCK));
        flushAndClear();

        assertTrue(adapter.existsOpenSessionForConnector(connectorId));
        assertTrue(adapter.existsOpenSessionForVehicle(vehicleId));
        assertFalse(adapter.existsOpenSessionForConnector(UUID.randomUUID()));

        adapter.save(adapter.findById(active.id()).orElseThrow().stop(BigDecimal.ONE, CLOCK));
        flushAndClear();

        assertFalse(adapter.existsOpenSessionForConnector(connectorId));
        assertFalse(adapter.existsOpenSessionForVehicle(vehicleId));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
