package org.evchargingplatform.pricing.domain.exception;

import java.util.UUID;

public class TariffNotFoundException extends RuntimeException {
    public TariffNotFoundException(String message) {
        super(message);
    }

    public static TariffNotFoundException forStation(UUID stationId) {
        return new TariffNotFoundException("Station " + stationId
                + " has no tariff yet; an operator sets one with PUT /stations/" + stationId + "/tariff");
    }
}
