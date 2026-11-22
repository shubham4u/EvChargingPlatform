package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.AddConnectorCommand;
import org.evchargingplatform.station.application.port.out.ConnectorRepository;
import org.evchargingplatform.station.application.port.out.StationRepository;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorType;
import org.evchargingplatform.station.domain.Station;
import org.evchargingplatform.station.domain.exception.ConnectorNotFoundException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConnectorApplicationService}.
 */
@ExtendWith(MockitoExtension.class)
class ConnectorApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ConnectorRepository connectorRepository;

    @Mock
    private StationRepository stationRepository;

    private ConnectorApplicationService service;

    private final Station station = Station.register("ST-001", "Central", "GB",
            new BigDecimal("51.507400"), new BigDecimal("-0.127800"));

    @BeforeEach
    void setUp() {
        service = new ConnectorApplicationService(connectorRepository, stationRepository, CLOCK);
    }

    @Test
    @DisplayName("should add a connector to an existing station")
    void shouldAddConnector() {
        when(stationRepository.findById(station.id())).thenReturn(Optional.of(station));
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connector result = service.addConnector(new AddConnectorCommand(station.id(), ConnectorType.CCS2, 150));

        assertEquals(station.id(), result.stationId());
        assertEquals(ConnectorType.CCS2, result.connectorType());
        assertEquals(150, result.maxPowerKw());
    }

    @Test
    @DisplayName("should reject a connector for an unknown station")
    void shouldRejectUnknownStation() {
        UUID stationId = UUID.randomUUID();
        when(stationRepository.findById(stationId)).thenReturn(Optional.empty());

        assertThrows(StationNotFoundException.class,
                () -> service.addConnector(new AddConnectorCommand(stationId, ConnectorType.TYPE2, 22)));
        verify(connectorRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject a non-positive power rating")
    void shouldRejectNonPositivePower() {
        when(stationRepository.findById(station.id())).thenReturn(Optional.of(station));

        assertThrows(IllegalArgumentException.class,
                () -> service.addConnector(new AddConnectorCommand(station.id(), ConnectorType.TYPE2, 0)));
    }

    @Test
    @DisplayName("findAtStation should throw when the connector is not at that station")
    void shouldThrowWhenConnectorNotAtStation() {
        UUID connectorId = UUID.randomUUID();
        when(connectorRepository.findByIdAndStationId(connectorId, station.id())).thenReturn(Optional.empty());

        assertThrows(ConnectorNotFoundException.class, () -> service.findAtStation(station.id(), connectorId));
    }
}
