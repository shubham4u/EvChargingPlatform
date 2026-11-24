package org.evchargingplatform.pricing.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a session costs and why. Components are shown rounded for display; {@link #total} is rounded
 * once from the unrounded components, so it can differ from their sum by a penny.
 */
public record Price(UUID tariffId, String currency, BigDecimal sessionFee, BigDecimal energyCost, BigDecimal timeCost,
        BigDecimal minutes, BigDecimal total) {
}
