package org.evchargingplatform.pricing.application.port.in;

import org.evchargingplatform.pricing.domain.Price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Prices a finished session at the tariff it started under. Called in-process by Charging on stop.
 */
public interface PriceSessionUseCase {
    Price price(UUID tariffId, BigDecimal energyKwh, Instant startedAt, Instant stoppedAt);
}
