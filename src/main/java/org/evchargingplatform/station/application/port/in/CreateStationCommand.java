package org.evchargingplatform.station.application.port.in;

import java.math.BigDecimal;

public record CreateStationCommand(
        String externalReference,
        String name,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude) {
}
