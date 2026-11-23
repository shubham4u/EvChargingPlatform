package org.evchargingplatform.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Tariff} definition rules and session pricing.
 */
class TariffTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final UUID STATION = UUID.randomUUID();

    private static Tariff tariff(String perKwh, String perMinute, String fee) {
        return Tariff.define(STATION, "gbp", new BigDecimal(perKwh), new BigDecimal(perMinute), new BigDecimal(fee),
                UUID.randomUUID(), CLOCK);
    }

    @Test
    @DisplayName("prices fee + energy + time: 0.50 + 42.75 kWh x 0.45 + 30 min x 0.05 = 21.24")
    void shouldPriceSession() {
        Price price = tariff("0.45", "0.05", "0.50").priceFor(new BigDecimal("42.75"), T0, T0.plus(Duration.ofMinutes(30)));

        assertEquals("GBP", price.currency());
        assertEquals(new BigDecimal("0.50"), price.sessionFee());
        assertEquals(new BigDecimal("19.24"), price.energyCost());   // 19.2375 shown rounded
        assertEquals(new BigDecimal("1.50"), price.timeCost());
        assertEquals(new BigDecimal("21.24"), price.total());        // 21.2375 rounded once
    }

    @Test
    @DisplayName("rounds the total once from unrounded parts, half up")
    void shouldRoundTotalOnce() {
        // energy 0.125 + time 0.125 = 0.25 exactly; rounding each part first would give 0.13 + 0.13 = 0.26
        Price price = tariff("0.125", "0.125", "0").priceFor(BigDecimal.ONE, T0, T0.plus(Duration.ofMinutes(1)));

        assertEquals(new BigDecimal("0.25"), price.total());
        assertEquals(new BigDecimal("0.13"), price.energyCost());
    }

    @Test
    @DisplayName("charges part-minutes pro rata")
    void shouldChargePartMinutes() {
        Price price = tariff("0", "0.60", "0").priceFor(BigDecimal.ZERO, T0, T0.plusSeconds(90));

        assertEquals(new BigDecimal("1.500000"), price.minutes());
        assertEquals(new BigDecimal("0.90"), price.total());
    }

    @Test
    @DisplayName("rejects negative prices and unknown currencies")
    void shouldValidate() {
        assertThrows(IllegalArgumentException.class, () -> tariff("-0.01", "0", "0"));
        assertThrows(IllegalArgumentException.class, () -> Tariff.define(STATION, "XYZ1", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, null, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> Tariff.define(STATION, null, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, null, CLOCK));
    }
}
