package org.evchargingplatform.reservation.application;

import org.evchargingplatform.reservation.application.port.in.CreateReservationCommand;
import org.evchargingplatform.reservation.application.port.out.ReservationEventPublisher;
import org.evchargingplatform.reservation.domain.Reservation;
import org.evchargingplatform.reservation.domain.ReservationStatus;
import org.evchargingplatform.reservation.domain.exception.ReservationNotFoundException;
import org.evchargingplatform.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReservationApplicationService}.
 * <p>
 * Uses a mock repository and a real {@link ObjectProvider} wrapper around a mock
 * event publisher to test use case orchestration in isolation.
 */
@ExtendWith(MockitoExtension.class)
class ReservationApplicationServiceTest {

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CHARGER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationEventPublisher eventPublisher;

    private ReservationApplicationService service;

    @BeforeEach
    void setUp() {
        ObjectProvider<ReservationEventPublisher> provider = new TestEventPublisherProvider(eventPublisher);
        service = new ReservationApplicationService(reservationRepository, provider, CLOCK);
    }

    /**
     * Simple {@link ObjectProvider} implementation that always returns the given publisher.
     * Avoids Mockito's inability to mock ObjectProvider on Java 25.
     */
    private static class TestEventPublisherProvider implements ObjectProvider<ReservationEventPublisher> {
        private final ReservationEventPublisher publisher;

        TestEventPublisherProvider(ReservationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public ReservationEventPublisher getObject() {
            return publisher;
        }

        @Override
        public ReservationEventPublisher getObject(Object... args) {
            return publisher;
        }

        @Override
        public ReservationEventPublisher getIfAvailable() {
            return publisher;
        }

        @Override
        public ReservationEventPublisher getIfUnique() {
            return publisher;
        }

        @Override
        public void ifAvailable(Consumer<ReservationEventPublisher> consumer) {
            consumer.accept(publisher);
        }

        @Override
        public void ifUnique(Consumer<ReservationEventPublisher> consumer) {
            consumer.accept(publisher);
        }

        @Override
        public Iterator<ReservationEventPublisher> iterator() {
            return java.util.Collections.singleton(publisher).iterator();
        }

        @Override
        public Spliterator<ReservationEventPublisher> spliterator() {
            return java.util.Collections.singleton(publisher).spliterator();
        }

        @Override
        public Stream<ReservationEventPublisher> stream() {
            return Stream.of(publisher);
        }
    }

    @Nested
    @DisplayName("create")
    class CreateUseCase {

        @Test
        @DisplayName("should create, persist, and publish a reservation")
        void shouldCreateReservation() {
            Instant startTime = FIXED_NOW.plus(Duration.ofMinutes(5));
            CreateReservationCommand command = new CreateReservationCommand(
                    STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, startTime);

            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Reservation result = service.create(command);

            assertNotNull(result);
            assertEquals(ReservationStatus.CREATED, result.status());
            assertEquals(STATION_ID, result.stationId());

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            verify(eventPublisher).reservationCreated(any(Reservation.class));
        }

        @Test
        @DisplayName("should throw when startTime is in the past")
        void shouldThrowWhenStartTimeIsInPast() {
            Instant pastStart = FIXED_NOW.minus(Duration.ofMinutes(5));
            CreateReservationCommand command = new CreateReservationCommand(
                    STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID, pastStart);

            assertThrows(IllegalArgumentException.class, () -> service.create(command));
            verify(reservationRepository, never()).save(any());
            verify(eventPublisher, never()).reservationCreated(any());
        }
    }

    @Nested
    @DisplayName("cancel")
    class CancelUseCase {

        @Test
        @DisplayName("should cancel a CREATED reservation and publish event")
        void shouldCancelReservation() {
            Reservation reservation = createReservation();
            when(reservationRepository.findById(reservation.id())).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Reservation result = service.cancel(reservation.id());

            assertEquals(ReservationStatus.CANCELLED, result.status());
            verify(eventPublisher).reservationCancelled(any(Reservation.class));
        }

        @Test
        @DisplayName("should throw ReservationNotFoundException when reservation does not exist")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(ReservationNotFoundException.class, () -> service.cancel(id));
            verify(eventPublisher, never()).reservationCancelled(any());
        }
    }

    @Nested
    @DisplayName("complete")
    class CompleteUseCase {

        @Test
        @DisplayName("should complete an ACTIVE reservation and publish event")
        void shouldCompleteActiveReservation() {
            Reservation active = createReservation().activate(CLOCK);
            when(reservationRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Reservation result = service.complete(active.id());

            assertEquals(ReservationStatus.COMPLETED, result.status());
            verify(eventPublisher).reservationCompleted(any(Reservation.class));
        }

        @Test
        @DisplayName("should throw IllegalStateException when completing a CREATED reservation")
        void shouldThrowWhenCompletingCreatedReservation() {
            Reservation created = createReservation();
            when(reservationRepository.findById(created.id())).thenReturn(Optional.of(created));

            assertThrows(IllegalStateException.class, () -> service.complete(created.id()));
            verify(eventPublisher, never()).reservationCompleted(any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdUseCase {

        @Test
        @DisplayName("should return the reservation when found")
        void shouldReturnReservationWhenFound() {
            Reservation reservation = createReservation();
            when(reservationRepository.findById(reservation.id())).thenReturn(Optional.of(reservation));

            Reservation result = service.findById(reservation.id());

            assertEquals(reservation, result);
        }

        @Test
        @DisplayName("should throw ReservationNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(ReservationNotFoundException.class, () -> service.findById(id));
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAllUseCase {

        @Test
        @DisplayName("should return all reservations")
        void shouldReturnAllReservations() {
            List<Reservation> reservations = List.of(createReservation(), createReservation());
            when(reservationRepository.findAll()).thenReturn(reservations);

            List<Reservation> result = service.findAll();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no reservations exist")
        void shouldReturnEmptyList() {
            when(reservationRepository.findAll()).thenReturn(List.of());

            List<Reservation> result = service.findAll();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("expireReservations")
    class ExpireUseCase {

        @Test
        @DisplayName("should expire all expired CREATED and ACTIVE reservations")
        void shouldExpireExpiredReservations() {
            Reservation expiredCreated = createReservation();
            Reservation expiredActive = createReservation().activate(CLOCK);
            when(reservationRepository.findExpired(any(), any()))
                    .thenReturn(List.of(expiredCreated, expiredActive));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.expireReservations();

            verify(reservationRepository, times(2)).save(any(Reservation.class));
            verify(eventPublisher, times(2)).reservationExpired(any(Reservation.class));
        }

        @Test
        @DisplayName("should do nothing when no expired reservations exist")
        void shouldDoNothingWhenNoExpiredReservations() {
            when(reservationRepository.findExpired(any(), any())).thenReturn(List.of());

            service.expireReservations();

            verify(reservationRepository, never()).save(any());
            verify(eventPublisher, never()).reservationExpired(any());
        }
    }

    private Reservation createReservation() {
        return Reservation.create(STATION_ID, CHARGER_ID, USER_ID, VEHICLE_ID,
                FIXED_NOW.plus(Duration.ofMinutes(5)), CLOCK);
    }
}