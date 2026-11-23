package org.evchargingplatform.pricing.adapter.out.persistence;

import org.evchargingplatform.pricing.application.port.out.TariffRepository;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.infrastructure.persistence.TariffEntity;
import org.evchargingplatform.pricing.infrastructure.persistence.TariffJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter for the tariff repository port. Tariffs are insert-only.
 */
@Component
public class TariffJpaAdapter implements TariffRepository {

    private final TariffJpaRepository repository;

    public TariffJpaAdapter(TariffJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Tariff save(Tariff t) {
        return toDomain(repository.save(new TariffEntity(t.id(), t.stationId(), t.currency(), t.pricePerKwh(),
                t.pricePerMinute(), t.sessionFee(), t.definedBy(), t.createdAt())));
    }

    @Override
    public Optional<Tariff> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Tariff> findLatestForStation(UUID stationId) {
        return repository.findFirstByStationIdOrderByCreatedAtDesc(stationId).map(this::toDomain);
    }

    private Tariff toDomain(TariffEntity e) {
        return new Tariff(e.id(), e.stationId(), e.currency(), e.pricePerKwh(), e.pricePerMinute(), e.sessionFee(),
                e.definedBy(), e.createdAt());
    }
}
