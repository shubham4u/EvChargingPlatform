package org.evchargingplatform.charging.application;

import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.out.ChargingSessionEventPublisher;
import org.evchargingplatform.charging.application.port.out.ChargingSessionRepository;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorInUseException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.evchargingplatform.charging.domain.exception.VehicleAlreadyChargingException;
import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.evchargingplatform.payments.application.port.in.ChargingPaymentUseCase;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.exception.PaymentDeclinedException;
import org.evchargingplatform.pricing.application.port.in.GetTariffUseCase;
import org.evchargingplatform.pricing.application.port.in.PriceSessionUseCase;
import org.evchargingplatform.pricing.domain.Price;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.domain.exception.TariffNotFoundException;
import org.evchargingplatform.reservation.application.port.in.ActivateReservationUseCase;
import org.evchargingplatform.reservation.application.port.in.CompleteReservationUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorsUseCase;
import org.evchargingplatform.station.domain.exception.ConnectorNotFoundException;
import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.evchargingplatform.station.domain.ConnectorReservationProjection.ReservationProjectionStatus;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotOwnedByUserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
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
 * Unit tests for {@link ChargingSessionApplicationService}.
 * <p>
 * Uses mock repositories and a real {@link ObjectProvider} wrapper around a mock
 * event publisher to test use case orchestration in isolation. The user, vehicle and
 * connector lookups are mocks that return normally unless a test makes them throw.
 */
@ExtendWith(MockitoExtension.class)
class ChargingSessionApplicationServiceTest {

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CONNECTOR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final Tariff TARIFF = Tariff.define(STATION_ID, "GBP", new BigDecimal("0.45"),
            new BigDecimal("0.05"), new BigDecimal("0.50"), UUID.randomUUID(), CLOCK);
    private static final PaymentMethod CARD = new PaymentMethod(USER_ID, "tok_visa", "visa", "4242", FIXED_NOW);

    @Mock
    private ChargingSessionRepository chargingSessionRepository;

    @Mock
    private ChargingSessionEventPublisher eventPublisher;

    @Mock
    private ConnectorReservationProjectionRepository projectionRepository;

    @Mock
    private GetUserUseCase getUserUseCase;

    @Mock
    private GetVehicleUseCase getVehicleUseCase;

    @Mock
    private GetConnectorsUseCase getConnectorsUseCase;

    @Mock
    private ActivateReservationUseCase activateReservationUseCase;

    @Mock
    private CompleteReservationUseCase completeReservationUseCase;

    @Mock
    private GetTariffUseCase getTariffUseCase;

    @Mock
    private PriceSessionUseCase priceSessionUseCase;

    @Mock
    private ChargingPaymentUseCase chargingPaymentUseCase;

    private ChargingSessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = newService(new TestEventPublisherProvider(eventPublisher));
        // Every station is priced unless a test says otherwise; holds succeed by default (the mock returns null).
        lenient().when(getTariffUseCase.currentFor(STATION_ID)).thenReturn(TARIFF);
    }

    private ChargingSessionApplicationService newService(ObjectProvider<ChargingSessionEventPublisher> provider) {
        return new ChargingSessionApplicationService(chargingSessionRepository, provider, CLOCK, projectionRepository,
                getUserUseCase, getVehicleUseCase, getConnectorsUseCase, activateReservationUseCase,
                completeReservationUseCase, getTariffUseCase, priceSessionUseCase, chargingPaymentUseCase);
    }

    /**
     * Simple {@link ObjectProvider} implementation that returns the given publisher,
     * or behaves as an empty provider when the publisher is {@code null}.
     * Avoids Mockito's inability to mock ObjectProvider on Java 25.
     */
    private static class TestEventPublisherProvider implements ObjectProvider<ChargingSessionEventPublisher> {
        private final ChargingSessionEventPublisher publisher;

        TestEventPublisherProvider(ChargingSessionEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public ChargingSessionEventPublisher getObject() {
            return publisher;
        }

        @Override
        public ChargingSessionEventPublisher getObject(Object... args) {
            return publisher;
        }

        @Override
        public ChargingSessionEventPublisher getIfAvailable() {
            return publisher;
        }

        @Override
        public ChargingSessionEventPublisher getIfUnique() {
            return publisher;
        }

        @Override
        public void ifAvailable(Consumer<ChargingSessionEventPublisher> consumer) {
            if (publisher != null) {
                consumer.accept(publisher);
            }
        }

        @Override
        public void ifUnique(Consumer<ChargingSessionEventPublisher> consumer) {
            if (publisher != null) {
                consumer.accept(publisher);
            }
        }

        @Override
        public Iterator<ChargingSessionEventPublisher> iterator() {
            return publisher == null ? Collections.emptyIterator() : Collections.singleton(publisher).iterator();
        }

        @Override
        public Spliterator<ChargingSessionEventPublisher> spliterator() {
            return publisher == null ? Collections.<ChargingSessionEventPublisher>emptyList().spliterator()
                    : Collections.singleton(publisher).spliterator();
        }

        @Override
        public Stream<ChargingSessionEventPublisher> stream() {
            return publisher == null ? Stream.empty() : Stream.of(publisher);
        }
    }

    @Nested
    @DisplayName("start")
    class StartUseCase {

        @Test
        @DisplayName("should save twice and publish Requested then Started for a walk-up session")
        void shouldStartWalkUpSession() {
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = service.start(command(null));

            assertEquals(ChargingSessionStatus.ACTIVE, result.status());
            assertEquals(FIXED_NOW, result.startedAt());
            assertNull(result.reservationId());

            ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
            InOrder inOrder = inOrder(chargingSessionRepository, eventPublisher);
            inOrder.verify(chargingSessionRepository).save(captor.capture());
            inOrder.verify(eventPublisher).sessionRequested(any(ChargingSession.class));
            inOrder.verify(chargingSessionRepository).save(captor.capture());
            inOrder.verify(eventPublisher).sessionStarted(any(ChargingSession.class));

            assertEquals(ChargingSessionStatus.REQUESTED, captor.getAllValues().get(0).status());
            assertEquals(ChargingSessionStatus.ACTIVE, captor.getAllValues().get(1).status());
            verify(getUserUseCase).findById(USER_ID);
            verify(getVehicleUseCase).findOwnedBy(VEHICLE_ID, USER_ID);
            verify(getConnectorsUseCase).findAtStation(STATION_ID, CONNECTOR_ID);
            verify(projectionRepository, never()).findByReservationId(any());
            assertEquals(TARIFF.id(), result.tariffId(), "the tariff in force is locked in");
            verifyNoInteractions(activateReservationUseCase);
        }

        @Test
        @DisplayName("should start a session when the reservation projection matches")
        void shouldStartWithValidReservation() {
            when(projectionRepository.findByReservationId(RESERVATION_ID))
                    .thenReturn(Optional.of(projection(ReservationProjectionStatus.RESERVED, CONNECTOR_ID, USER_ID)));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = service.start(command(RESERVATION_ID));

            assertEquals(ChargingSessionStatus.ACTIVE, result.status());
            assertEquals(RESERVATION_ID, result.reservationId());
            verify(activateReservationUseCase).activate(RESERVATION_ID);
            verify(eventPublisher).sessionRequested(any(ChargingSession.class));
            verify(eventPublisher).sessionStarted(any(ChargingSession.class));
        }

        @Test
        @DisplayName("should throw ConnectorNotReservedException when no projection exists")
        void shouldThrowWhenProjectionMissing() {
            when(projectionRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

            assertThrows(ConnectorNotReservedException.class, () -> service.start(command(RESERVATION_ID)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should throw ConnectorNotReservedException when the reservation was released")
        void shouldThrowWhenProjectionReleased() {
            when(projectionRepository.findByReservationId(RESERVATION_ID))
                    .thenReturn(Optional.of(projection(ReservationProjectionStatus.RELEASED, CONNECTOR_ID, USER_ID)));

            assertThrows(ConnectorNotReservedException.class, () -> service.start(command(RESERVATION_ID)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should throw ConnectorNotReservedException when the reservation is for another connector")
        void shouldThrowWhenConnectorMismatch() {
            when(projectionRepository.findByReservationId(RESERVATION_ID))
                    .thenReturn(Optional.of(projection(ReservationProjectionStatus.RESERVED, UUID.randomUUID(), USER_ID)));

            assertThrows(ConnectorNotReservedException.class, () -> service.start(command(RESERVATION_ID)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should throw ConnectorNotReservedException when the reservation belongs to another user")
        void shouldThrowWhenUserMismatch() {
            when(projectionRepository.findByReservationId(RESERVATION_ID))
                    .thenReturn(Optional.of(projection(ReservationProjectionStatus.RESERVED, CONNECTOR_ID, UUID.randomUUID())));

            assertThrows(ConnectorNotReservedException.class, () -> service.start(command(RESERVATION_ID)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject an unknown user without saving or publishing")
        void shouldThrowWhenUserUnknown() {
            when(getUserUseCase.findById(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

            assertThrows(UserNotFoundException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject a vehicle that belongs to another user")
        void shouldThrowWhenVehicleNotOwned() {
            when(getVehicleUseCase.findOwnedBy(VEHICLE_ID, USER_ID))
                    .thenThrow(new VehicleNotOwnedByUserException(VEHICLE_ID, USER_ID));

            assertThrows(VehicleNotOwnedByUserException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject a connector that is not at the station")
        void shouldThrowWhenConnectorNotAtStation() {
            when(getConnectorsUseCase.findAtStation(STATION_ID, CONNECTOR_ID))
                    .thenThrow(new ConnectorNotFoundException(STATION_ID, CONNECTOR_ID));

            assertThrows(ConnectorNotFoundException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject a connector that already has an open session")
        void shouldThrowWhenConnectorInUse() {
            when(chargingSessionRepository.existsOpenSessionForConnector(CONNECTOR_ID)).thenReturn(true);

            assertThrows(ConnectorInUseException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject a vehicle that is already charging elsewhere")
        void shouldThrowWhenVehicleAlreadyCharging() {
            when(chargingSessionRepository.existsOpenSessionForVehicle(VEHICLE_ID)).thenReturn(true);

            assertThrows(VehicleAlreadyChargingException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should reject a walk-up start while a reservation holds the connector")
        void shouldThrowWhenWalkUpOnHeldConnector() {
            when(projectionRepository.findByStationIdAndStatus(STATION_ID, ReservationProjectionStatus.RESERVED))
                    .thenReturn(List.of(projection(ReservationProjectionStatus.RESERVED, CONNECTOR_ID, UUID.randomUUID())));

            assertThrows(ConnectorInUseException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should ignore a reservation hold whose expiry has passed")
        void shouldIgnoreExpiredHold() {
            ConnectorReservationProjection expired = new ConnectorReservationProjection(
                    RESERVATION_ID, STATION_ID, CONNECTOR_ID, UUID.randomUUID(),
                    ReservationProjectionStatus.RESERVED, FIXED_NOW.minusSeconds(60), FIXED_NOW);
            when(projectionRepository.findByStationIdAndStatus(STATION_ID, ReservationProjectionStatus.RESERVED))
                    .thenReturn(List.of(expired));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(ChargingSessionStatus.ACTIVE, service.start(command(null)).status());
        }

        @Test
        @DisplayName("should take the hold before saving or announcing the session")
        void shouldAuthorizeBeforeSaving() {
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = service.start(command(null));

            InOrder inOrder = inOrder(chargingPaymentUseCase, chargingSessionRepository, eventPublisher);
            inOrder.verify(chargingPaymentUseCase).authorizeHold(result.id(), USER_ID, "GBP");
            inOrder.verify(chargingSessionRepository).save(any(ChargingSession.class));
            inOrder.verify(eventPublisher).sessionRequested(any(ChargingSession.class));
        }

        @Test
        @DisplayName("should refuse to start, saving and announcing nothing, when the card is declined")
        void shouldNotStartWhenDeclined() {
            when(chargingPaymentUseCase.authorizeHold(any(), eq(USER_ID), eq("GBP")))
                    .thenThrow(new PaymentDeclinedException("card_declined", "declined"));

            assertThrows(PaymentDeclinedException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
        }

        @Test
        @DisplayName("should refuse to start at a station with no tariff, without touching the card")
        void shouldNotStartWithoutTariff() {
            when(getTariffUseCase.currentFor(STATION_ID)).thenThrow(TariffNotFoundException.forStation(STATION_ID));

            assertThrows(TariffNotFoundException.class, () -> service.start(command(null)));
            verifyNothingSavedOrPublished();
            verifyNoInteractions(chargingPaymentUseCase);
        }

        @Test
        @DisplayName("should start without error when no event publisher is available")
        void shouldStartWhenPublisherUnavailable() {
            service = newService(new TestEventPublisherProvider(null));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = assertDoesNotThrow(() -> service.start(command(null)));

            assertEquals(ChargingSessionStatus.ACTIVE, result.status());
            verify(chargingSessionRepository, times(2)).save(any(ChargingSession.class));
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("stop")
    class StopUseCase {

        @Test
        @DisplayName("should stop an ACTIVE session and publish Stopped with total energy")
        void shouldStopActiveSession() {
            ChargingSession active = createSession().start(CLOCK);
            BigDecimal totalEnergyKwh = new BigDecimal("18.250");
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = service.stop(active.id(), totalEnergyKwh);

            assertEquals(ChargingSessionStatus.STOPPED, result.status());
            assertEquals(FIXED_NOW, result.stoppedAt());

            ArgumentCaptor<ChargingSession> captor = ArgumentCaptor.forClass(ChargingSession.class);
            verify(eventPublisher).sessionStopped(captor.capture());
            assertEquals(totalEnergyKwh, captor.getValue().totalEnergyKwh());
        }

        @Test
        @DisplayName("should price the session at its tariff, capture that amount, and end STOPPED")
        void shouldPriceAndCapture() {
            ChargingSession active = pricedSession().start(CLOCK);
            Price price = new Price(TARIFF.id(), "GBP", new BigDecimal("0.50"), new BigDecimal("8.21"), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO, new BigDecimal("8.71"));
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(priceSessionUseCase.price(eq(TARIFF.id()), eq(new BigDecimal("18.250")), any(), any())).thenReturn(price);
            when(chargingPaymentUseCase.capture(active.id(), new BigDecimal("8.71"))).thenReturn(captured(active, "8.71"));

            ChargingSession result = service.stop(active.id(), new BigDecimal("18.250"));

            assertEquals(ChargingSessionStatus.STOPPED, result.status());
            assertEquals(new BigDecimal("8.71"), result.totalCost());
            assertEquals("GBP", result.currency());
        }

        @Test
        @DisplayName("should end PAYMENT_FAILED, still freeing the connector and completing the reservation, when capture fails")
        void shouldFlagFailedCapture() {
            ChargingSession active = ChargingSession.create(STATION_ID, CONNECTOR_ID, USER_ID, VEHICLE_ID, RESERVATION_ID, CLOCK)
                    .withTariff(TARIFF.id()).start(CLOCK);
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(priceSessionUseCase.price(any(), any(), any(), any())).thenReturn(new Price(TARIFF.id(), "GBP",
                    new BigDecimal("0.50"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.50")));
            when(chargingPaymentUseCase.capture(any(), any())).thenReturn(failedCapture(active));

            ChargingSession result = service.stop(active.id(), BigDecimal.ONE);

            assertEquals(ChargingSessionStatus.PAYMENT_FAILED, result.status());
            assertEquals(new BigDecimal("1.50"), result.totalCost(), "the amount owed is still recorded");
            verify(eventPublisher).sessionStopped(result);
            verify(completeReservationUseCase).completeIfActive(RESERVATION_ID);
        }

        @Test
        @DisplayName("should stop a session started before pricing existed without pricing or capturing it")
        void shouldStopLegacySessionUnpriced() {
            ChargingSession legacy = createSession().start(CLOCK);
            when(chargingSessionRepository.findById(legacy.id())).thenReturn(Optional.of(legacy));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(ChargingSessionStatus.STOPPED, service.stop(legacy.id(), BigDecimal.ONE).status());
            verifyNoInteractions(priceSessionUseCase, chargingPaymentUseCase);
        }

        @Test
        @DisplayName("should complete the reservation when a reservation-backed session stops")
        void shouldCompleteReservationOnStop() {
            ChargingSession active = ChargingSession.create(STATION_ID, CONNECTOR_ID, USER_ID, VEHICLE_ID, RESERVATION_ID, CLOCK)
                    .start(CLOCK);
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.stop(active.id(), BigDecimal.ONE);

            verify(completeReservationUseCase).completeIfActive(RESERVATION_ID);
        }

        @Test
        @DisplayName("should not touch reservations when a walk-up session stops")
        void shouldNotCompleteForWalkUp() {
            ChargingSession active = createSession().start(CLOCK);
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.stop(active.id(), BigDecimal.ONE);

            verifyNoInteractions(completeReservationUseCase);
        }

        @Test
        @DisplayName("should throw ChargingSessionNotFoundException when session does not exist")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(chargingSessionRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(ChargingSessionNotFoundException.class, () -> service.stop(id, BigDecimal.TEN));
            verify(chargingSessionRepository, never()).save(any());
            verify(eventPublisher, never()).sessionStopped(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException when stopping a session that is not ACTIVE")
        void shouldThrowWhenNotActive() {
            ChargingSession requested = createSession();
            when(chargingSessionRepository.findById(requested.id())).thenReturn(Optional.of(requested));

            assertThrows(IllegalStateException.class, () -> service.stop(requested.id(), BigDecimal.TEN));
            verify(chargingSessionRepository, never()).save(any());
            verify(eventPublisher, never()).sessionStopped(any());
        }

        @Test
        @DisplayName("should stop without error when no event publisher is available")
        void shouldStopWhenPublisherUnavailable() {
            service = newService(new TestEventPublisherProvider(null));
            ChargingSession active = createSession().start(CLOCK);
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChargingSession result = assertDoesNotThrow(() -> service.stop(active.id(), BigDecimal.ONE));

            assertEquals(ChargingSessionStatus.STOPPED, result.status());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("payOutstanding")
    class PayOutstandingUseCase {

        private ChargingSession unpaid() {
            ChargingSession session = pricedSession().start(CLOCK).stop(BigDecimal.ONE, CLOCK)
                    .priced(new BigDecimal("5.00"), "GBP").paymentFailed(CLOCK);
            when(chargingSessionRepository.findById(session.id())).thenReturn(Optional.of(session));
            return session;
        }

        @Test
        @DisplayName("collects the amount owed and turns PAYMENT_FAILED into STOPPED")
        void shouldRecover() {
            ChargingSession session = unpaid();
            when(chargingPaymentUseCase.collectOutstanding(session.id(), new BigDecimal("5.00")))
                    .thenReturn(captured(session, "5.00"));
            when(chargingSessionRepository.save(any(ChargingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(ChargingSessionStatus.STOPPED, service.payOutstanding(session.id()).status());
        }

        @Test
        @DisplayName("is 402 with the reason, and changes nothing, when the card still fails")
        void shouldReportStillFailing() {
            ChargingSession session = unpaid();
            when(chargingPaymentUseCase.collectOutstanding(any(), any())).thenReturn(failedCapture(session));

            assertThrows(PaymentDeclinedException.class, () -> service.payOutstanding(session.id()));
            verify(chargingSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a session that owes nothing")
        void shouldRefusePaidSession() {
            ChargingSession active = createSession().start(CLOCK);
            when(chargingSessionRepository.findById(active.id())).thenReturn(Optional.of(active));

            assertThrows(IllegalStateException.class, () -> service.payOutstanding(active.id()));
            verifyNoInteractions(chargingPaymentUseCase);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdUseCase {

        @Test
        @DisplayName("should return the session when found")
        void shouldReturnSessionWhenFound() {
            ChargingSession session = createSession();
            when(chargingSessionRepository.findById(session.id())).thenReturn(Optional.of(session));

            ChargingSession result = service.findById(session.id());

            assertEquals(session, result);
        }

        @Test
        @DisplayName("should throw ChargingSessionNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(chargingSessionRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(ChargingSessionNotFoundException.class, () -> service.findById(id));
        }
    }

    @Nested
    @DisplayName("getAllChargingSessions")
    class FindAllUseCase {

        @Test
        @DisplayName("should return all sessions")
        void shouldReturnAllSessions() {
            when(chargingSessionRepository.findAll()).thenReturn(List.of(createSession(), createSession()));

            List<ChargingSession> result = service.getAllChargingSessions();

            assertEquals(2, result.size());
        }
    }

    private void verifyNothingSavedOrPublished() {
        verify(chargingSessionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private static StartChargingSessionCommand command(UUID reservationId) {
        return new StartChargingSessionCommand(STATION_ID, CONNECTOR_ID, USER_ID, VEHICLE_ID, reservationId);
    }

    private static ConnectorReservationProjection projection(
            ReservationProjectionStatus status, UUID connectorId, UUID userId) {
        return new ConnectorReservationProjection(
                RESERVATION_ID, STATION_ID, connectorId, userId, status, FIXED_NOW.plusSeconds(600), FIXED_NOW);
    }

    private ChargingSession pricedSession() {
        return createSession().withTariff(TARIFF.id());
    }

    private static PaymentIntent captured(ChargingSession session, String amount) {
        return PaymentIntent.authorized(session.id(), USER_ID, "GBP", new BigDecimal("50.00"), CARD, "sim_auth", CLOCK)
                .captured(new BigDecimal(amount), CLOCK);
    }

    private static PaymentIntent failedCapture(ChargingSession session) {
        return PaymentIntent.authorized(session.id(), USER_ID, "GBP", new BigDecimal("50.00"), CARD, "sim_auth", CLOCK)
                .captureFailed("capture_failed", "The issuer rejected the final charge", CLOCK);
    }

    private ChargingSession createSession() {
        return ChargingSession.create(STATION_ID, CONNECTOR_ID, USER_ID, VEHICLE_ID, null, CLOCK);
    }
}
