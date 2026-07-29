package org.evchargingplatform.station.application;

import org.evchargingplatform.station.application.port.in.CreateStationCommand;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.application.port.out.StationRepository;
import org.evchargingplatform.station.application.port.out.StationEventPublisher;
import org.evchargingplatform.station.domain.Station;
import org.evchargingplatform.station.domain.exception.DuplicateStationException;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StationApplicationService implements CreateStationUseCase, GetStationsUseCase {
    private final StationRepository stationRepository;
    private final ObjectProvider<StationEventPublisher> eventPublisher;

    public StationApplicationService(StationRepository stationRepository,
                                     ObjectProvider<StationEventPublisher> eventPublisher) {
        this.stationRepository = stationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Station create(CreateStationCommand command) {
        if (stationRepository.existsByExternalReference(command.externalReference())) {
            throw new DuplicateStationException(command.externalReference());
        }
        Station station = stationRepository.save(Station.register(
                command.externalReference(), command.name(), command.countryCode(),
                command.latitude(), command.longitude()));
        eventPublisher.ifAvailable(publisher -> publisher.stationRegistered(station));
        return station;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Station> findAll() {
        return stationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "stations", key = "#stationId")
    public Station findById(UUID stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));
    }
}
