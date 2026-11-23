package org.evchargingplatform.pricing.application;

import org.evchargingplatform.pricing.application.port.in.GetTariffUseCase;
import org.evchargingplatform.pricing.application.port.in.PriceSessionUseCase;
import org.evchargingplatform.pricing.application.port.in.SetTariffCommand;
import org.evchargingplatform.pricing.application.port.in.SetTariffUseCase;
import org.evchargingplatform.pricing.application.port.out.TariffRepository;
import org.evchargingplatform.pricing.domain.Price;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.domain.exception.TariffNotFoundException;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Tariff management and session pricing (FR-6). The station is checked through the Station context's
 * query use case, the same in-process approach as the other modules.
 */
@Service
@Transactional
public class PricingApplicationService implements SetTariffUseCase, GetTariffUseCase, PriceSessionUseCase {

    private final TariffRepository tariffRepository;
    private final GetStationsUseCase getStationsUseCase;
    private final Clock clock;

    public PricingApplicationService(TariffRepository tariffRepository, GetStationsUseCase getStationsUseCase, Clock clock) {
        this.tariffRepository = tariffRepository;
        this.getStationsUseCase = getStationsUseCase;
        this.clock = clock;
    }

    @Override
    public Tariff setTariff(SetTariffCommand command) {
        getStationsUseCase.findById(command.stationId());
        return tariffRepository.save(Tariff.define(command.stationId(), command.currency(), command.pricePerKwh(),
                command.pricePerMinute(), command.sessionFee(), command.operatorId(), clock));
    }

    @Override
    @Transactional(readOnly = true)
    public Tariff currentFor(UUID stationId) {
        return tariffRepository.findLatestForStation(stationId)
                .orElseThrow(() -> TariffNotFoundException.forStation(stationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Price price(UUID tariffId, BigDecimal energyKwh, Instant startedAt, Instant stoppedAt) {
        Tariff tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new TariffNotFoundException("Tariff not found: " + tariffId));
        return tariff.priceFor(energyKwh, startedAt, stoppedAt);
    }
}
