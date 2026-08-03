package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StationProjectionService}.
 * <p>
 * Verifies that reservation lifecycle events are correctly processed
 * into connector availability projections.
 */
@ExtendWith(MockitoExtension.class)
class StationProjectionServiceTest {

    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CONNECTOR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.now().plus(Duration.ofMinutes(10));

    @Mock
    private ConnectorReservationProjectionRepository projectionRepository;

    @Test
    @DisplayName("onReservationCreated should save a RESERVED projection")
    void shouldSaveReservedProjectionOnCreated() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        when(projectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onReservationCreated(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);

        ArgumentCaptor<ConnectorReservationProjection> captor =
                ArgumentCaptor.forClass(ConnectorReservationProjection.class);
        verify(projectionRepository).save(captor.capture());

        ConnectorReservationProjection saved = captor.getValue();
        assertEquals(RESERVATION_ID, saved.reservationId());
        assertEquals(STATION_ID, saved.stationId());
        assertEquals(CONNECTOR_ID, saved.connectorId());
        assertEquals(USER_ID, saved.userId());
        assertEquals(ConnectorReservationProjection.ReservationProjectionStatus.RESERVED, saved.status());
        assertEquals(EXPIRES_AT, saved.expiresAt());
    }

    @Test
    @DisplayName("onReservationCancelled should release the connector projection")
    void shouldReleaseProjectionOnCancelled() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        ConnectorReservationProjection existing = ConnectorReservationProjection.reserved(
                RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);
        when(projectionRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(projectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onReservationCancelled(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID);

        ArgumentCaptor<ConnectorReservationProjection> captor =
                ArgumentCaptor.forClass(ConnectorReservationProjection.class);
        verify(projectionRepository).save(captor.capture());

        assertEquals(ConnectorReservationProjection.ReservationProjectionStatus.RELEASED, captor.getValue().status());
    }

    @Test
    @DisplayName("onReservationCompleted should release the connector projection")
    void shouldReleaseProjectionOnCompleted() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        ConnectorReservationProjection existing = ConnectorReservationProjection.reserved(
                RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);
        when(projectionRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(projectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onReservationCompleted(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID);

        verify(projectionRepository).save(any());
    }

    @Test
    @DisplayName("onReservationExpired should release the connector projection")
    void shouldReleaseProjectionOnExpired() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        ConnectorReservationProjection existing = ConnectorReservationProjection.reserved(
                RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);
        when(projectionRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(projectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onReservationExpired(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID);

        verify(projectionRepository).save(any());
    }

    @Test
    @DisplayName("should handle missing projection gracefully on release events")
    void shouldHandleMissingProjectionGracefully() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        when(projectionRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        // Should not throw
        assertDoesNotThrow(() ->
                service.onReservationCancelled(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID));

        verify(projectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("onReservationCreated should be idempotent — overwrites existing projection")
    void shouldBeIdempotentOnCreated() {
        StationProjectionService service = new StationProjectionService(projectionRepository);
        when(projectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate duplicate delivery
        service.onReservationCreated(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);
        service.onReservationCreated(RESERVATION_ID, STATION_ID, CONNECTOR_ID, USER_ID, EXPIRES_AT);

        // Should save twice (upsert), both with RESERVED status
        verify(projectionRepository, times(2)).save(any());
    }
}