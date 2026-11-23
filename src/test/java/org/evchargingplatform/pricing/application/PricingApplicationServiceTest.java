package org.evchargingplatform.pricing.application;

import org.evchargingplatform.pricing.application.port.in.SetTariffCommand;
import org.evchargingplatform.pricing.application.port.out.TariffRepository;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.domain.exception.TariffNotFoundException;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PricingApplicationService}.
 */
@ExtendWith(MockitoExtension.class)
class PricingApplicationServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final UUID STATION = UUID.randomUUID();

    @Mock
    private TariffRepository repository;
    @Mock
    private GetStationsUseCase getStationsUseCase;

    private PricingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PricingApplicationService(repository, getStationsUseCase, CLOCK);
    }

    @Test
    @DisplayName("setting a tariff checks the station and saves a new version")
    void shouldSetTariff() {
        when(repository.save(any(Tariff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tariff tariff = service.setTariff(new SetTariffCommand(STATION, "EUR", new BigDecimal("0.59"), BigDecimal.ZERO,
                BigDecimal.ZERO, UUID.randomUUID()));

        assertEquals(STATION, tariff.stationId());
        assertEquals("EUR", tariff.currency());
        verify(getStationsUseCase).findById(STATION);
    }

    @Test
    @DisplayName("setting a tariff for an unknown station fails and saves nothing")
    void shouldRejectUnknownStation() {
        when(getStationsUseCase.findById(STATION)).thenThrow(new StationNotFoundException(STATION));

        assertThrows(StationNotFoundException.class, () -> service.setTariff(new SetTariffCommand(STATION, "GBP",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a station without a tariff says how to set one")
    void shouldExplainMissingTariff() {
        when(repository.findLatestForStation(STATION)).thenReturn(Optional.empty());

        TariffNotFoundException thrown = assertThrows(TariffNotFoundException.class, () -> service.currentFor(STATION));
        assertTrue(thrown.getMessage().contains("PUT /stations/" + STATION + "/tariff"));
    }

    @Test
    @DisplayName("prices a session at the tariff it was given, not the station's current one")
    void shouldPriceAtGivenTariff() {
        Tariff old = Tariff.define(STATION, "GBP", new BigDecimal("0.40"), BigDecimal.ZERO, BigDecimal.ZERO, null, CLOCK);
        when(repository.findById(old.id())).thenReturn(Optional.of(old));

        assertEquals(new BigDecimal("4.00"), service.price(old.id(), BigDecimal.TEN, T0, T0.plusSeconds(60)).total());
        verify(repository, never()).findLatestForStation(any());
    }
}
