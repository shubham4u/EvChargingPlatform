package org.evchargingplatform.pricing.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public record SetTariffCommand(
        UUID stationId,
        String currency,
        BigDecimal pricePerKwh,
        BigDecimal pricePerMinute,
        BigDecimal sessionFee,
        UUID operatorId) {
}
