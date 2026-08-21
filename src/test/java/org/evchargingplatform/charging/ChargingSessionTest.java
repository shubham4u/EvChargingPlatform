package org.evchargingplatform.charging;

import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ChargingSession} aggregate.
 * <p>
 * Uses a fixed clock so all time-based assertions are deterministic.
 */
class ChargingSessionTest {

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CONNECTOR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    /**
     * Test-only builder for {@link ChargingSession}. Not a test class itself —
     * used by the @Nested classes below to construct sessions with sensible
     * defaults, overridden only where a test cares about a specific value.
     */
    static class ChargingSessionBuilder {
        private UUID stationId = STATION_ID;
        private UUID connectorId = CONNECTOR_ID;
        private UUID userId = USER_ID;
        private UUID vehicleId = VEHICLE_ID;
        private UUID reservationId = null;
        private Clock clock = CLOCK;

        ChargingSessionBuilder withStationId(UUID stationId) {
            this.stationId = stationId;
            return this;
        }

        ChargingSessionBuilder withConnectorId(UUID connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        ChargingSessionBuilder withUserId(UUID userId) {
            this.userId = userId;
            return this;
        }

        ChargingSessionBuilder withVehicleId(UUID vehicleId) {
            this.vehicleId = vehicleId;
            return this;
        }

        ChargingSessionBuilder withReservationId(UUID reservationId) {
            this.reservationId = reservationId;
            return this;
        }

        ChargingSessionBuilder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        ChargingSession build() {
            return ChargingSession.create(stationId, connectorId, userId, vehicleId, reservationId, clock);
        }
    }

    private static ChargingSessionBuilder aSession() {
        return new ChargingSessionBuilder();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create a session with REQUESTED status and null lifecycle fields")
        void shouldCreateSessionWithDefaults() {
            ChargingSession session = aSession().build();

            assertNotNull(session.id());
            assertTrue(session.chargingSessionNumber() != null && !session.chargingSessionNumber().isBlank());
            assertEquals(STATION_ID, session.stationId());
            assertEquals(CONNECTOR_ID, session.connectorId());
            assertEquals(USER_ID, session.userId());
            assertEquals(VEHICLE_ID, session.vehicleId());
            assertNull(session.reservationId());
            assertEquals(ChargingSessionStatus.REQUESTED, session.status());
            assertNull(session.startedAt());
            assertNull(session.stoppedAt());
            assertNull(session.totalEnergyKwh());
            assertEquals(FIXED_NOW, session.createdAt());
            assertEquals(FIXED_NOW, session.updatedAt());
        }

        @Test
        @DisplayName("should accept an optional reservationId for reservation-backed sessions")
        void shouldCreateSessionWithReservationId() {
            UUID reservationId = UUID.randomUUID();

            ChargingSession session = aSession().withReservationId(reservationId).build();

            assertEquals(reservationId, session.reservationId());
        }

        @Test
        @DisplayName("should generate unique ids and session numbers")
        void shouldGenerateUniqueIdentifiers() {
            ChargingSession first = aSession().build();
            ChargingSession second = aSession().build();

            assertNotEquals(first.id(), second.id());
            assertNotEquals(first.chargingSessionNumber(), second.chargingSessionNumber());
        }
    }

    @Nested
    @DisplayName("start")
    class Start {

        @Test
        @DisplayName("should activate a REQUESTED session and stamp startedAt")
        void shouldStartRequestedSession() {
            ChargingSession requested = aSession().build();

            ChargingSession active = requested.start(CLOCK);

            assertEquals(ChargingSessionStatus.ACTIVE, active.status());
            assertEquals(FIXED_NOW, active.startedAt());
            assertNull(active.stoppedAt());
            assertEquals(requested.id(), active.id());
            assertEquals(requested.createdAt(), active.createdAt());
        }

        @Test
        @DisplayName("should throw when starting an already ACTIVE session")
        void shouldThrowWhenStartingActiveSession() {
            ChargingSession active = aSession().build().start(CLOCK);

            assertThrows(IllegalStateException.class, () -> active.start(CLOCK));
        }

        @Test
        @DisplayName("should throw when starting a STOPPED session")
        void shouldThrowWhenStartingStoppedSession() {
            ChargingSession stopped = aSession().build().start(CLOCK).stop(BigDecimal.TEN, CLOCK);

            assertThrows(IllegalStateException.class, () -> stopped.start(CLOCK));
        }
    }

    @Nested
    @DisplayName("stop")
    class Stop {

        @Test
        @DisplayName("should stop an ACTIVE session, stamp stoppedAt, and record totalEnergyKwh")
        void shouldStopActiveSession() {
            ChargingSession active = aSession().build().start(CLOCK);
            BigDecimal totalEnergyKwh = new BigDecimal("12.750");

            ChargingSession stopped = active.stop(totalEnergyKwh, CLOCK);

            assertEquals(ChargingSessionStatus.STOPPED, stopped.status());
            assertEquals(FIXED_NOW, stopped.stoppedAt());
            assertEquals(totalEnergyKwh, stopped.totalEnergyKwh());
            assertEquals(active.startedAt(), stopped.startedAt());
        }

        @Test
        @DisplayName("should throw when stopping a REQUESTED session that was never started")
        void shouldThrowWhenStoppingRequestedSession() {
            ChargingSession requested = aSession().build();

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> requested.stop(BigDecimal.TEN, CLOCK));

            assertTrue(exception.getMessage().contains("can only be stopped from ACTIVE status"));
        }

        @Test
        @DisplayName("should throw when stopping an already STOPPED session")
        void shouldThrowWhenStoppingStoppedSession() {
            ChargingSession stopped = aSession().build().start(CLOCK).stop(BigDecimal.TEN, CLOCK);

            assertThrows(IllegalStateException.class, () -> stopped.stop(BigDecimal.ONE, CLOCK));
        }
    }
}
