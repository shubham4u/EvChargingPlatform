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
import java.util.List;
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

    @Test
    @DisplayName("should detect overlapping open reservations on the same charger only")
    void shouldDetectOverlap() {
        UUID chargerId = UUID.randomUUID();
        // window [12:05, 12:15)
        Reservation existing = Reservation.create(UUID.randomUUID(), chargerId, UUID.randomUUID(),
                UUID.randomUUID(), FIXED_NOW.plus(Duration.ofMinutes(5)), CLOCK);
        adapter.save(existing);
        flushAndClear();
        List<ReservationStatus> open = List.of(ReservationStatus.CREATED, ReservationStatus.ACTIVE);

        assertTrue(adapter.existsOverlapping(chargerId, open, at(10), at(20)));
        assertTrue(adapter.existsOverlapping(chargerId, open, at(0), at(6)));
        assertFalse(adapter.existsOverlapping(chargerId, open, at(15), at(25)), "touching end is not an overlap");
        assertFalse(adapter.existsOverlapping(chargerId, open, at(0), at(5)), "touching start is not an overlap");
        assertFalse(adapter.existsOverlapping(UUID.randomUUID(), open, at(10), at(20)));

        adapter.save(adapter.findById(existing.id()).orElseThrow().cancel(CLOCK));
        flushAndClear();

        assertFalse(adapter.existsOverlapping(chargerId, open, at(10), at(20)), "cancelled no longer blocks");
    }

    private static Instant at(int minutesAfterNow) {
        return FIXED_NOW.plus(Duration.ofMinutes(minutesAfterNow));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
