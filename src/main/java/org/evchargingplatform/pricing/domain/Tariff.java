package org.evchargingplatform.pricing.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * A station's price list at one point in time: a fixed fee per session, plus a price per kWh and per
 * minute plugged in.
 * <p>
 * Tariffs are immutable. Changing a station's prices defines a new tariff, and a session keeps the id
 * of the one in force when it started, so its bill can't change while it is charging.
 */
public record Tariff(UUID id, UUID stationId, String currency, BigDecimal pricePerKwh, BigDecimal pricePerMinute,
        BigDecimal sessionFee, UUID definedBy, Instant createdAt) implements Serializable {

    public static Tariff define(UUID stationId, String currency, BigDecimal pricePerKwh, BigDecimal pricePerMinute,
            BigDecimal sessionFee, UUID definedBy, Clock clock) {
        String code = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code such as GBP or EUR");
        }
        return new Tariff(UUID.randomUUID(), stationId, code, nonNegative("pricePerKwh", pricePerKwh),
                nonNegative("pricePerMinute", pricePerMinute), nonNegative("sessionFee", sessionFee), definedBy,
                Instant.now(clock));
    }

    /**
     * Prices a session. Components are summed at full precision and the total is rounded once, half up, to
     * 2 decimal places, so the bill never depends on how the components were rounded.
     */
    public Price priceFor(BigDecimal energyKwh, Instant startedAt, Instant stoppedAt) {
        BigDecimal minutes = BigDecimal.valueOf(Duration.between(startedAt, stoppedAt).toMillis())
                .divide(BigDecimal.valueOf(60_000), 6, RoundingMode.HALF_UP);
        BigDecimal energy = energyKwh.multiply(pricePerKwh);
        BigDecimal time = minutes.multiply(pricePerMinute);
        return new Price(id, currency, money(sessionFee), money(energy), money(time), minutes,
                money(sessionFee.add(energy).add(time)));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(String field, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be zero or more");
        }
        return value;
    }
}
