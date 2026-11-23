package org.evchargingplatform.pricing.application.port.out;

import org.evchargingplatform.pricing.domain.Tariff;

import java.util.Optional;
import java.util.UUID;

public interface TariffRepository {
    Tariff save(Tariff tariff);

    Optional<Tariff> findById(UUID id);

    Optional<Tariff> findLatestForStation(UUID stationId);
}
