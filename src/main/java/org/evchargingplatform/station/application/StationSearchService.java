package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.application.port.in.GetConnectorAvailabilityUseCase;
import org.evchargingplatform.station.application.port.in.SearchStationsUseCase;
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
import org.evchargingplatform.station.domain.GeoDistance;
import org.evchargingplatform.station.domain.Station;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Station discovery (FR-1) and live connector availability (FR-3), built entirely from the Station
 * Service's own data: the catalog plus the reservation and occupancy read models fed by Kafka. It never
 * calls the Charging or Reservation contexts, so search keeps working (slightly stale) if they are busy
 * or down, and adds no load to them.
 * <p>
 * Location search narrows candidates with a bounding-box query, then applies the exact haversine
 * distance in memory. That is fine for thousands of stations; PostGIS is the next step beyond that.
 */
@Service
@Transactional(readOnly = true)
public class StationSearchService implements SearchStationsUseCase, GetConnectorAvailabilityUseCase {
    private final StationRepository stationRepository;
    private final ConnectorRepository connectorRepository;
    private final ConnectorOccupancyRepository occupancyRepository;
    private final ConnectorReservationProjectionRepository reservationProjectionRepository;
    private final Clock clock;

    public StationSearchService(StationRepository stationRepository,
                                ConnectorRepository connectorRepository,
                                ConnectorOccupancyRepository occupancyRepository,
                                ConnectorReservationProjectionRepository reservationProjectionRepository,
                                Clock clock) {
        this.stationRepository = stationRepository;
        this.connectorRepository = connectorRepository;
        this.occupancyRepository = occupancyRepository;
        this.reservationProjectionRepository = reservationProjectionRepository;
        this.clock = clock;
    }

    @Override
    public List<StationSearchResult> search(StationSearchQuery query) {
        List<Station> candidates = query.hasLocation()
                ? stationRepository.findActiveWithin(GeoDistance.boundingBox(query.latitude(), query.longitude(), query.radiusKm()))
                : stationRepository.findActive();

        Map<UUID, Double> distances = new java.util.HashMap<>();
        if (query.hasLocation()) {
            candidates = candidates.stream()
                    .filter(station -> {
                        double km = GeoDistance.km(query.latitude(), query.longitude(),
                                station.latitude().doubleValue(), station.longitude().doubleValue());
                        distances.put(station.id(), km);
                        return km <= query.radiusKm();
                    })
                    .toList();
        }

        Map<UUID, List<ConnectorStatus>> connectorsByStation = statusesByStation(
                candidates.stream().map(Station::id).toList());

        List<StationSearchResult> results = new ArrayList<>();
        for (Station station : candidates) {
            List<ConnectorStatus> connectors = connectorsByStation.getOrDefault(station.id(), List.of()).stream()
                    .filter(status -> matches(status, query))
                    .toList();
            if (query.filtersConnectors() && connectors.isEmpty()) {
                continue;
            }
            Double distanceKm = query.hasLocation() ? Math.round(distances.get(station.id()) * 100) / 100.0 : null;
            results.add(new StationSearchResult(station, distanceKm, connectors));
        }

        Comparator<StationSearchResult> order = query.hasLocation()
                ? Comparator.comparing(StationSearchResult::distanceKm)
                : Comparator.comparing(result -> result.station().name(), String.CASE_INSENSITIVE_ORDER);
        return results.stream().sorted(order.thenComparing(result -> result.station().id())).limit(query.limit()).toList();
    }

    @Override
    public List<ConnectorStatus> findByStation(UUID stationId) {
        if (stationRepository.findById(stationId).isEmpty()) {
            throw new StationNotFoundException(stationId);
        }
        return statusesByStation(List.of(stationId)).getOrDefault(stationId, List.of());
    }

    private Map<UUID, List<ConnectorStatus>> statusesByStation(Collection<UUID> stationIds) {
        if (stationIds.isEmpty()) {
            return Map.of();
        }
        List<Connector> connectors = connectorRepository.findByStationIds(stationIds);
        List<UUID> connectorIds = connectors.stream().map(Connector::id).toList();

        Set<UUID> occupied = occupancyRepository.findByConnectorIds(connectorIds).stream()
                .map(ConnectorOccupancy::connectorId)
                .collect(Collectors.toSet());
        // Same rule as Charging's walk-up check: an expired hold no longer blocks, even before its expiry event lands.
        Instant now = Instant.now(clock);
        Set<UUID> held = reservationProjectionRepository
                .findByConnectorIdsAndStatus(connectorIds, ReservationProjectionStatus.RESERVED).stream()
                .filter(p -> p.expiresAt() == null || p.expiresAt().isAfter(now))
                .map(ConnectorReservationProjection::connectorId)
                .collect(Collectors.toSet());

        return connectors.stream()
                .map(connector -> new ConnectorStatus(connector, occupied.contains(connector.id())
                        ? ConnectorAvailability.OCCUPIED
                        : held.contains(connector.id()) ? ConnectorAvailability.RESERVED : ConnectorAvailability.AVAILABLE))
                .collect(Collectors.groupingBy(status -> status.connector().stationId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())));
    }

    private static boolean matches(ConnectorStatus status, StationSearchQuery query) {
        Connector connector = status.connector();
        return (query.connectorType() == null || connector.connectorType() == query.connectorType())
                && (query.minPowerKw() == null || connector.maxPowerKw() >= query.minPowerKw())
                && (!query.availableOnly() || status.availability() == ConnectorAvailability.AVAILABLE);
    }
}
