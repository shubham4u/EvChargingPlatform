package org.evchargingplatform.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Reservation} aggregate.
 * <p>
 * Uses a fixed clock so all time-based assertions are deterministic.
 */
class ReservationTest {

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CHARGER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("create")
    class CreateReservation {

        @Test
        @DisplayName("should create a reservation with CREATED status when startTime is in the future")
        void shouldCreateReservationWithValidData() {
            Instant startTime = FIXED_NOW.plus(Duration.ofMinutes(5));

            Reservation reservation = Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, startTime, CLOCK);

            assertNotNull(reservation.id());
            assertTrue(reservation.reservationNumber().startsWith("RSV-"));
            assertEquals(STATION_ID, reservation.stationId());
            assertEquals(CHARGER_ID, reservation.chargerId());
            assertEquals(USER_ID, reservation.userId());
            assertEquals(VEHICLE_ID, reservation.vehicleId());
            assertEquals(startTime, reservation.startTime());
            assertEquals(startTime.plus(Duration.ofMinutes(10)), reservation.expirationTime());
            assertEquals(ReservationStatus.CREATED, reservation.status());
            assertEquals(FIXED_NOW, reservation.createdAt());
            assertEquals(FIXED_NOW, reservation.updatedAt());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when startTime is in the past")
        void shouldThrowWhenStartTimeIsInPast() {
            Instant pastStart = FIXED_NOW.minus(Duration.ofMinutes(5));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, pastStart, CLOCK));

            assertTrue(exception.getMessage().contains("startTime must be in the future"));
        }

        @Test
        @DisplayName("should generate unique reservation numbers")
        void shouldGenerateUniqueReservationNumbers() {
            Instant startTime = FIXED_NOW.plus(Duration.ofMinutes(5));

            Reservation r1 = Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, startTime, CLOCK);
            Reservation r2 = Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, startTime, CLOCK);

            assertNotEquals(r1.reservationNumber(), r2.reservationNumber());
            assertNotEquals(r1.id(), r2.id());
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelReservation {

        @Test
        @DisplayName("should cancel a CREATED reservation")
        void shouldCancelCreatedReservation() {
            Reservation reservation = createReservation();

            Reservation cancelled = reservation.cancel(CLOCK);

            assertEquals(ReservationStatus.CANCELLED, cancelled.status());
            assertEquals(reservation.id(), cancelled.id());
            assertEquals(reservation.createdAt(), cancelled.createdAt());
        }

        @Test
        @DisplayName("should throw when cancelling an already CANCELLED reservation")
        void shouldThrowWhenCancellingCancelledReservation() {
            Reservation cancelled = createReservation().cancel(CLOCK);

            assertThrows(IllegalStateException.class, () -> cancelled.cancel(CLOCK));
        }

        @Test
        @DisplayName("should throw when cancelling a COMPLETED reservation")
        void shouldThrowWhenCancellingCompletedReservation() {
            Reservation active = createReservation().activate(CLOCK);
            Reservation completed = active.complete(CLOCK);

            assertThrows(IllegalStateException.class, () -> completed.cancel(CLOCK));
        }
    }

    @Nested
    @DisplayName("complete")
    class CompleteReservation {

        @Test
        @DisplayName("should complete an ACTIVE reservation")
        void shouldCompleteActiveReservation() {
            Reservation active = createReservation().activate(CLOCK);

            Reservation completed = active.complete(CLOCK);

            assertEquals(ReservationStatus.COMPLETED, completed.status());
        }

        @Test
        @DisplayName("should throw when completing a CREATED reservation (only ACTIVE can complete)")
        void shouldThrowWhenCompletingCreatedReservation() {
            Reservation created = createReservation();

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> created.complete(CLOCK));

            assertTrue(exception.getMessage().contains("Only active reservations can complete"));
        }

        @Test
        @DisplayName("should throw when completing an already COMPLETED reservation")
        void shouldThrowWhenCompletingCompletedReservation() {
            Reservation completed = createReservation().activate(CLOCK).complete(CLOCK);

            assertThrows(IllegalStateException.class, () -> completed.complete(CLOCK));
        }
    }

    @Nested
    @DisplayName("expire")
    class ExpireReservation {

        @Test
        @DisplayName("should expire a CREATED reservation")
        void shouldExpireCreatedReservation() {
            Reservation reservation = createReservation();

            Reservation expired = reservation.expire(CLOCK);

            assertEquals(ReservationStatus.EXPIRED, expired.status());
        }

        @Test
        @DisplayName("should expire an ACTIVE reservation")
        void shouldExpireActiveReservation() {
            Reservation active = createReservation().activate(CLOCK);

            Reservation expired = active.expire(CLOCK);

            assertEquals(ReservationStatus.EXPIRED, expired.status());
        }

        @Test
        @DisplayName("should be idempotent when expiring an already EXPIRED reservation")
        void shouldBeIdempotentWhenExpiringExpiredReservation() {
            Reservation expired = createReservation().expire(CLOCK);

            Reservation result = expired.expire(CLOCK);

            assertSame(expired, result);
            assertEquals(ReservationStatus.EXPIRED, result.status());
        }

        @Test
        @DisplayName("should be idempotent when expiring a CANCELLED reservation")
        void shouldBeIdempotentWhenExpiringCancelledReservation() {
            Reservation cancelled = createReservation().cancel(CLOCK);

            Reservation result = cancelled.expire(CLOCK);

            assertSame(cancelled, result);
            assertEquals(ReservationStatus.CANCELLED, result.status());
        }

        @Test
        @DisplayName("should be idempotent when expiring a COMPLETED reservation")
        void shouldBeIdempotentWhenExpiringCompletedReservation() {
            Reservation completed = createReservation().activate(CLOCK).complete(CLOCK);

            Reservation result = completed.expire(CLOCK);

            assertSame(completed, result);
            assertEquals(ReservationStatus.COMPLETED, result.status());
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("should return true when expiration time has passed and status is CREATED")
        void shouldReturnTrueWhenExpiredAndCreated() {
            Reservation reservation = createReservation();

            assertTrue(reservation.isExpired(FIXED_NOW.plus(Duration.ofMinutes(16))));
        }

        @Test
        @DisplayName("should return false when expiration time has not passed")
        void shouldReturnFalseWhenNotExpired() {
            Reservation reservation = createReservation();

            assertFalse(reservation.isExpired(FIXED_NOW.plus(Duration.ofMinutes(5))));
        }

        @Test
        @DisplayName("should return false when status is terminal even if time has passed")
        void shouldReturnFalseWhenTerminal() {
            Reservation cancelled = createReservation().cancel(CLOCK);

            assertFalse(cancelled.isExpired(FIXED_NOW.plus(Duration.ofMinutes(16))));
        }
    }

    @Nested
    @DisplayName("activate")
    class ActivateReservation {

        @Test
        @DisplayName("should activate a CREATED reservation")
        void shouldActivateCreatedReservation() {
            Reservation reservation = createReservation();

            Reservation active = reservation.activate(CLOCK);

            assertEquals(ReservationStatus.ACTIVE, active.status());
        }

        @Test
        @DisplayName("should throw when activating a CANCELLED reservation")
        void shouldThrowWhenActivatingCancelledReservation() {
            Reservation cancelled = createReservation().cancel(CLOCK);

            assertThrows(IllegalStateException.class, () -> cancelled.activate(CLOCK));
        }
    }

    private Reservation createReservation() {
        return Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID,
                FIXED_NOW.plus(Duration.ofMinutes(5)), CLOCK);
    }
}