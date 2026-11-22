package org.evchargingplatform.reservation.adapter.out.persistence;

import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.infrastructure.persistence.ReservationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link ReservationJpaAdapter}.
 * <p>
 * Runs against the real Flyway schema on H2, clearing the persistence context
 * between saves so each update behaves like a separate request.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import(ReservationJpaAdapter.class)
class ReservationJpaAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired
    private ReservationJpaAdapter adapter;

    @Autowired
    private ReservationJpaRepository jpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should persist more than one update to the same reservation")
    void shouldPersistSuccessiveUpdates() {
        Reservation created = Reservation.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), FIXED_NOW.plus(Duration.ofMinutes(5)), CLOCK);
        adapter.save(created);
        flushAndClear();

        adapter.save(adapter.findById(created.id()).orElseThrow().activate(CLOCK));
        flushAndClear();

        adapter.save(adapter.findById(created.id()).orElseThrow().complete(CLOCK));
        flushAndClear();

        assertEquals(ReservationStatus.COMPLETED, adapter.findById(created.id()).orElseThrow().status());
        assertEquals(2L, jpaRepository.findById(created.id()).orElseThrow().getVersion());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
