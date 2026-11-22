package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.AddConnectorCommand;
import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorsUseCase;
import org.evchargingplatform.station.application.port.out.ConnectorRepository;
import org.evchargingplatform.station.application.port.out.StationRepository;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.exception.ConnectorNotFoundException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the connectors that belong to a station.
 * <p>
 * Kept separate from {@link StationApplicationService} so connector lookups, which Charging and
 * Reservation make on every start/create, go straight to the repository rather than through the
 * station's Redis cache.
 */
@Service
@Transactional
public class ConnectorApplicationService implements AddConnectorUseCase, GetConnectorsUseCase {
    private final ConnectorRepository connectorRepository;
    private final StationRepository stationRepository;
    private final Clock clock;

    public ConnectorApplicationService(ConnectorRepository connectorRepository,
                                       StationRepository stationRepository,
                                       Clock clock) {
        this.connectorRepository = connectorRepository;
        this.stationRepository = stationRepository;
        this.clock = clock;
    }

    @Override
    public Connector addConnector(AddConnectorCommand command) {
        ensureStationExists(command.stationId());
        return connectorRepository.save(
                Connector.add(command.stationId(), command.connectorType(), command.maxPowerKw(), clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Connector> findByStation(UUID stationId) {
        ensureStationExists(stationId);
        return connectorRepository.findByStationId(stationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Connector findAtStation(UUID stationId, UUID connectorId) {
        return connectorRepository.findByIdAndStationId(connectorId, stationId)
                .orElseThrow(() -> new ConnectorNotFoundException(stationId, connectorId));
    }

    private void ensureStationExists(UUID stationId) {
        if (stationRepository.findById(stationId).isEmpty()) {
            throw new StationNotFoundException(stationId);
        }
    }
}
