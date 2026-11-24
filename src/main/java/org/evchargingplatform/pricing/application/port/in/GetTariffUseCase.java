package org.evchargingplatform.pricing.application.port.in;

import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.domain.exception.TariffNotFoundException;

import java.util.UUID;

/**
 * Query port for tariffs, also called in-process by Charging when a session starts.
 */
public interface GetTariffUseCase {
    /**
     * @throws TariffNotFoundException if the station has no tariff
     */
    Tariff currentFor(UUID stationId);
}
