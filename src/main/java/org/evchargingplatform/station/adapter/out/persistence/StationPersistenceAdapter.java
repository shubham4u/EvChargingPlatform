package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.application.port.out.StationRepository;
import org.evchargingplatform.station.domain.GeoDistance.BoundingBox;
import org.evchargingplatform.station.domain.Station;
import org.evchargingplatform.station.domain.StationStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StationPersistenceAdapter implements StationRepository {
    private final SpringDataStationRepository repository;

    StationPersistenceAdapter(SpringDataStationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Station save(Station station) {
        StationJpaEntity saved = repository.save(toEntity(station));
        return toDomain(saved);
    }

    @Override
    public List<Station> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Station> findById(UUID stationId) {
        return repository.findById(stationId).map(this::toDomain);
    }

    @Override
    public boolean existsByExternalReference(String externalReference) {
        return repository.existsByExternalReference(externalReference);
    }

    @Override
    public List<Station> findActive() {
        return repository.findByStatus(StationStatus.ACTIVE).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Station> findActiveWithin(BoundingBox box) {
        return repository.findByStatusAndLatitudeBetweenAndLongitudeBetween(StationStatus.ACTIVE,
                        BigDecimal.valueOf(box.minLat()), BigDecimal.valueOf(box.maxLat()),
                        BigDecimal.valueOf(box.minLng()), BigDecimal.valueOf(box.maxLng()))
                .stream().map(this::toDomain).toList();
    }

    private StationJpaEntity toEntity(Station station) {
        return new StationJpaEntity(station.id(), station.externalReference(), station.name(),
                station.countryCode(), station.latitude(), station.longitude(), station.status(), station.createdAt());
    }

    private Station toDomain(StationJpaEntity entity) {
        return new Station(entity.id, entity.externalReference, entity.name, entity.countryCode,
                entity.latitude, entity.longitude, entity.status, entity.createdAt);
    }
}
