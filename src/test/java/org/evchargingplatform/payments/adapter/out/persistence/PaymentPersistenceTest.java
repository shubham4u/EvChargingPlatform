package org.evchargingplatform.payments.adapter.out.persistence;

import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.PaymentStatus;
import org.evchargingplatform.pricing.adapter.out.persistence.TariffJpaAdapter;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.station.domain.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for payments and tariffs against the real Flyway schema (V1–V11) on H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import({PaymentJpaAdapter.class, TariffJpaAdapter.class})
class PaymentPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private PaymentJpaAdapter payments;
    @Autowired
    private TariffJpaAdapter tariffs;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("an intent survives authorize -> capture -> refund updates in separate persistence contexts")
    void shouldPersistIntentLifecycle() {
        UUID user = UUID.randomUUID();
        PaymentMethod card = payments.save(new PaymentMethod(user, "tok_visa", "visa", "4242", CLOCK.instant()));
        PaymentIntent intent = payments.save(PaymentIntent.authorized(UUID.randomUUID(), user, "GBP",
                new BigDecimal("50.00"), card, "sim_auth", CLOCK));
        flushAndClear();

        payments.save(payments.findBySessionId(intent.sessionId()).orElseThrow().captured(new BigDecimal("21.24"), CLOCK));
        flushAndClear();
        payments.save(payments.findById(intent.id()).orElseThrow().refunded(CLOCK));
        flushAndClear();

        PaymentIntent loaded = payments.findById(intent.id()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, loaded.status());
        assertEquals(0, new BigDecimal("21.24").compareTo(loaded.capturedAmount()));
        assertEquals("4242", loaded.cardLast4());
        assertEquals(1, payments.findByUserId(user).size());
        assertEquals("visa", payments.findForUser(user).orElseThrow().brand());
    }

    @Test
    @DisplayName("replacing a card overwrites the user's single saved card")
    void shouldReplaceCard() {
        UUID user = UUID.randomUUID();
        payments.save(new PaymentMethod(user, "tok_visa", "visa", "4242", CLOCK.instant()));
        flushAndClear();
        payments.save(new PaymentMethod(user, "tok_mastercard", "mastercard", "4444", CLOCK.instant()));
        flushAndClear();

        assertEquals("mastercard", payments.findForUser(user).orElseThrow().brand());
    }

    @Test
    @DisplayName("the latest tariff per station is current; older versions stay for sessions that used them")
    void shouldKeepTariffVersions() {
        // tariffs.station_id references stations; insert the row directly (the station adapter is package-private)
        Station station = Station.register("ST-P", "Priced", "GB", new BigDecimal("51.5"), new BigDecimal("-0.1"));
        entityManager.getEntityManager().createNativeQuery(
                        "INSERT INTO stations (id, external_reference, name, country_code, latitude, longitude, status, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .setParameter(1, station.id()).setParameter(2, station.externalReference()).setParameter(3, station.name())
                .setParameter(4, station.countryCode()).setParameter(5, station.latitude()).setParameter(6, station.longitude())
                .setParameter(7, station.status().name()).setParameter(8, station.createdAt())
                .executeUpdate();
        Tariff v1 = tariffs.save(Tariff.define(station.id(), "GBP", new BigDecimal("0.40"), BigDecimal.ZERO, BigDecimal.ZERO,
                null, CLOCK));
        Tariff v2 = tariffs.save(Tariff.define(station.id(), "GBP", new BigDecimal("0.45"), BigDecimal.ZERO, BigDecimal.ZERO,
                null, Clock.offset(CLOCK, java.time.Duration.ofMinutes(1))));
        flushAndClear();

        assertEquals(v2.id(), tariffs.findLatestForStation(station.id()).orElseThrow().id());
        assertTrue(tariffs.findById(v1.id()).isPresent());
        assertTrue(tariffs.findLatestForStation(UUID.randomUUID()).isEmpty());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
