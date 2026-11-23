package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.application.port.in.StationSearchQuery;
import org.evchargingplatform.station.application.port.in.StationSearchResult;
import org.evchargingplatform.station.application.port.out.ConnectorOccupancyRepository;
import org.evchargingplatform.station.application.port.out.ConnectorRepository;
import org.evchargingplatform.station.application.port.out.ConnectorReservationProjectionRepository;
import org.evchargingplatform.station.application.port.out.StationRepository;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorAvailability;
import org.evchargingplatform.station.domain.ConnectorOccupancy;
import org.evchargingplatform.station.domain.ConnectorReservationProjection;
import org.evchargingplatform.station.domain.ConnectorReservationProjection.ReservationProjectionStatus;
import org.evchargingplatform.station.domain.ConnectorType;
import org.evchargingplatform.station.domain.Station;
import org.evchargingplatform.station.domain.StationStatus;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StationSearchService}: distance filtering and ordering, per-connector filters,
 * and how occupancy and reservation holds become availability.
 */
@ExtendWith(MockitoExtension.class)
class StationSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    // Search from King's Cross. Euston is ~0.8 km away, Canary Wharf ~7.5 km, Brighton ~79 km.
    private static final double LAT = 51.5308, LNG = -0.1238;
    private final Station euston = station("Euston", "51.5282", "-0.1337");
    private final Station canaryWharf = station("Canary Wharf", "51.5054", "-0.0235");
    private final Station brighton = station("Brighton", "50.8225", "-0.1372");

    private final Connector eustonCcs = connector(euston, ConnectorType.CCS2, 150);
    private final Connector eustonType2 = connector(euston, ConnectorType.TYPE2, 22);
    private final Connector wharfCcs = connector(canaryWharf, ConnectorType.CCS2, 50);
    private final Connector brightonCcs = connector(brighton, ConnectorType.CCS2, 350);

    @Mock
    private StationRepository stationRepository;
    @Mock
    private ConnectorRepository connectorRepository;
    @Mock
    private ConnectorOccupancyRepository occupancyRepository;
    @Mock
    private ConnectorReservationProjectionRepository projectionRepository;

    private StationSearchService service;

    @BeforeEach
    void setUp() {
        service = new StationSearchService(stationRepository, connectorRepository, occupancyRepository, projectionRepository, CLOCK);
    }

    private void givenStations(List<Station> stations, List<Connector> connectors) {
        lenient().when(stationRepository.findActiveWithin(any())).thenReturn(stations);
        lenient().when(stationRepository.findActive()).thenReturn(stations);
        lenient().when(connectorRepository.findByStationIds(anyCollection())).thenReturn(connectors);
    }

    private static StationSearchQuery near(double radiusKm) {
        return new StationSearchQuery(LAT, LNG, radiusKm, null, null, false, 20);
    }

    @Nested
    @DisplayName("location")
    class Location {

        @Test
        @DisplayName("returns stations inside the radius, nearest first, with distances")
        void shouldFilterAndSortByDistance() {
            // the bounding box may return a station that is outside the circle; the exact check drops it
            givenStations(List.of(canaryWharf, brighton, euston), List.of(eustonCcs, wharfCcs, brightonCcs));

            List<StationSearchResult> results = service.search(near(10));

            assertEquals(List.of("Euston", "Canary Wharf"), results.stream().map(r -> r.station().name()).toList());
            assertEquals(0.8, results.get(0).distanceKm(), 0.1);
            assertEquals(7.49, results.get(1).distanceKm(), 0.05);
        }

        @Test
        @DisplayName("without a location, lists active stations by name with no distance")
        void shouldSortByNameWithoutLocation() {
            givenStations(List.of(euston, brighton, canaryWharf), List.of());

            List<StationSearchResult> results = service.search(new StationSearchQuery(null, null, 10, null, null, false, 20));

            assertEquals(List.of("Brighton", "Canary Wharf", "Euston"), results.stream().map(r -> r.station().name()).toList());
            assertNull(results.get(0).distanceKm());
            verify(stationRepository, never()).findActiveWithin(any());
        }

        @Test
        @DisplayName("stops at the limit")
        void shouldApplyLimit() {
            givenStations(List.of(euston, canaryWharf, brighton), List.of());

            assertEquals(1, service.search(new StationSearchQuery(LAT, LNG, 100, null, null, false, 1)).size());
        }
    }

    @Nested
    @DisplayName("connector filters")
    class ConnectorFilters {

        @Test
        @DisplayName("keeps only stations with a matching connector, and lists only the matching connectors")
        void shouldFilterByTypeAndPower() {
            givenStations(List.of(euston, canaryWharf), List.of(eustonCcs, eustonType2, wharfCcs));

            List<StationSearchResult> results = service.search(new StationSearchQuery(LAT, LNG, 10, ConnectorType.CCS2, 100, false, 20));

            assertEquals(1, results.size());
            assertEquals("Euston", results.get(0).station().name());
            assertEquals(List.of(eustonCcs), results.get(0).connectors().stream().map(ConnectorStatus::connector).toList());
        }

        @Test
        @DisplayName("without connector filters, keeps stations that have no connectors yet")
        void shouldKeepStationsWithoutConnectors() {
            givenStations(List.of(euston), List.of());

            List<StationSearchResult> results = service.search(near(10));

            assertEquals(1, results.size());
            assertTrue(results.get(0).connectors().isEmpty());
        }
    }

    @Nested
    @DisplayName("availability")
    class Availability {

        @Test
        @DisplayName("occupied beats reserved; an unexpired hold reserves; an expired hold is ignored")
        void shouldDeriveAvailability() {
            Connector wharfType2 = connector(canaryWharf, ConnectorType.TYPE2, 22);
            givenStations(List.of(euston, canaryWharf), List.of(eustonCcs, eustonType2, wharfCcs, wharfType2));
            when(occupancyRepository.findByConnectorIds(anyCollection()))
                    .thenReturn(List.of(new ConnectorOccupancy(eustonCcs.id(), euston.id(), UUID.randomUUID(), NOW, NOW)));
            when(projectionRepository.findByConnectorIdsAndStatus(anyCollection(), eq(ReservationProjectionStatus.RESERVED)))
                    .thenReturn(List.of(
                            hold(eustonCcs, NOW.plusSeconds(300)),      // also occupied: occupied wins
                            hold(eustonType2, NOW.plusSeconds(300)),
                            hold(wharfCcs, NOW.minusSeconds(1))));      // expired

            List<StationSearchResult> results = service.search(near(10));

            assertEquals(ConnectorAvailability.OCCUPIED, statusOf(results, eustonCcs));
            assertEquals(ConnectorAvailability.RESERVED, statusOf(results, eustonType2));
            assertEquals(ConnectorAvailability.AVAILABLE, statusOf(results, wharfCcs));
            assertEquals(ConnectorAvailability.AVAILABLE, statusOf(results, wharfType2));
            assertEquals(0, results.get(0).availableConnectors());
            assertEquals(2, results.get(1).availableConnectors());
        }

        @Test
        @DisplayName("available=true drops stations where nothing matching is free right now")
        void shouldFilterAvailableOnly() {
            givenStations(List.of(euston, canaryWharf), List.of(eustonCcs, wharfCcs));
            when(occupancyRepository.findByConnectorIds(anyCollection()))
                    .thenReturn(List.of(new ConnectorOccupancy(eustonCcs.id(), euston.id(), UUID.randomUUID(), NOW, NOW)));

            List<StationSearchResult> results = service.search(new StationSearchQuery(LAT, LNG, 10, ConnectorType.CCS2, null, true, 20));

            assertEquals(List.of("Canary Wharf"), results.stream().map(r -> r.station().name()).toList());
        }

        @Test
        @DisplayName("connector availability for one station, and 404 for an unknown station")
        void shouldListOneStation() {
            when(stationRepository.findById(euston.id())).thenReturn(Optional.of(euston));
            when(connectorRepository.findByStationIds(List.of(euston.id()))).thenReturn(List.of(eustonCcs, eustonType2));

            List<ConnectorStatus> statuses = service.findByStation(euston.id());

            assertEquals(2, statuses.size());
            assertTrue(statuses.stream().allMatch(s -> s.availability() == ConnectorAvailability.AVAILABLE));

            UUID unknown = UUID.randomUUID();
            when(stationRepository.findById(unknown)).thenReturn(Optional.empty());
            assertThrows(StationNotFoundException.class, () -> service.findByStation(unknown));
        }
    }

    @Nested
    @DisplayName("query validation")
    class QueryValidation {

        @Test
        @DisplayName("rejects half a location, out-of-range coordinates, radius and limit")
        void shouldRejectInvalidQueries() {
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(LAT, null, 10, null, null, false, 20));
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(91.0, LNG, 10, null, null, false, 20));
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(LAT, LNG, 0, null, null, false, 20));
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(LAT, LNG, 201, null, null, false, 20));
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(LAT, LNG, 10, null, 0, false, 20));
            assertThrows(IllegalArgumentException.class, () -> new StationSearchQuery(LAT, LNG, 10, null, null, false, 101));
        }
    }

    private static ConnectorAvailability statusOf(List<StationSearchResult> results, Connector connector) {
        return results.stream().flatMap(r -> r.connectors().stream())
                .filter(s -> s.connector().id().equals(connector.id()))
                .findFirst().orElseThrow().availability();
    }

    private static ConnectorReservationProjection hold(Connector connector, Instant expiresAt) {
        return new ConnectorReservationProjection(UUID.randomUUID(), connector.stationId(), connector.id(), UUID.randomUUID(),
                ReservationProjectionStatus.RESERVED, expiresAt, NOW);
    }

    private static Station station(String name, String lat, String lng) {
        return new Station(UUID.randomUUID(), "ST-" + name, name, "GB", new BigDecimal(lat), new BigDecimal(lng),
                StationStatus.ACTIVE, NOW);
    }

    private static Connector connector(Station station, ConnectorType type, int kw) {
        return new Connector(UUID.randomUUID(), station.id(), type, kw, NOW);
    }
}
