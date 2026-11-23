package org.evchargingplatform.pricing.application.port.in;

import org.evchargingplatform.pricing.domain.Tariff;

public interface SetTariffUseCase {
    /** Defines a new current tariff for the station; sessions already running keep their old one. */
    Tariff setTariff(SetTariffCommand command);
}
