package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.out.ConnectorOccupancyRepository;
import org.evchargingplatform.station.domain.ConnectorOccupancy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OccupancyProjectionService}, including redelivered and out-of-order events.
 */
@ExtendWith(MockitoExtension.class)
class OccupancyProjectionServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CONNECTOR_ID = UUID.randomUUID();
    private static final UUID SESSION_A = UUID.randomUUID();
    private static final UUID SESSION_B = UUID.randomUUID();

    @Mock
    private ConnectorOccupancyRepository repository;

    private OccupancyProjectionService service;

    @BeforeEach
    void setUp() {
        service = new OccupancyProjectionService(repository, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static ConnectorOccupancy showing(UUID sessionId, Instant startedAt) {
        return new ConnectorOccupancy(CONNECTOR_ID, STATION_ID, sessionId, startedAt, startedAt);
    }

    @Test
    @DisplayName("started marks the connector occupied by that session")
    void shouldOccupyOnStart() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.empty());

        service.onSessionStarted(SESSION_A, STATION_ID, CONNECTOR_ID, T0);

        ArgumentCaptor<ConnectorOccupancy> captor = ArgumentCaptor.forClass(ConnectorOccupancy.class);
        verify(repository).save(captor.capture());
        assertEquals(SESSION_A, captor.getValue().sessionId());
        assertEquals(STATION_ID, captor.getValue().stationId());
    }

    @Test
    @DisplayName("a redelivered start is harmless")
    void shouldBeIdempotentOnStart() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.of(showing(SESSION_A, T0)));

        service.onSessionStarted(SESSION_A, STATION_ID, CONNECTOR_ID, T0);

        verify(repository).save(any());
    }

    @Test
    @DisplayName("a late start of an older session does not replace a newer one")
    void shouldIgnoreStaleStart() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.of(showing(SESSION_B, T0.plusSeconds(3600))));

        service.onSessionStarted(SESSION_A, STATION_ID, CONNECTOR_ID, T0);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("stopped frees the connector when it shows that session")
    void shouldFreeOnStop() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.of(showing(SESSION_A, T0)));

        service.onSessionStopped(SESSION_A, STATION_ID, CONNECTOR_ID, T0.plusSeconds(60));

        verify(repository).deleteByConnectorId(CONNECTOR_ID);
    }

    @Test
    @DisplayName("a late stop of an older session leaves the newer session's occupancy alone")
    void shouldIgnoreStopOfOtherSession() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.of(showing(SESSION_B, T0.plusSeconds(3600))));

        service.onSessionStopped(SESSION_A, STATION_ID, CONNECTOR_ID, T0.plusSeconds(60));

        verify(repository, never()).deleteByConnectorId(any());
    }

    @Test
    @DisplayName("a redelivered stop is harmless")
    void shouldBeIdempotentOnStop() {
        when(repository.findByConnectorId(CONNECTOR_ID)).thenReturn(Optional.empty());

        service.onSessionStopped(SESSION_A, STATION_ID, CONNECTOR_ID, T0.plusSeconds(60));

        verify(repository, never()).deleteByConnectorId(any());
    }
}
