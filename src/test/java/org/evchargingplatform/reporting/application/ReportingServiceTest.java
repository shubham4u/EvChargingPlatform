package org.evchargingplatform.reporting.application;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.payments.application.port.in.GetPaymentUseCase;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.reporting.application.port.in.StationReport;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.domain.Station;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportingService}: counts and money for a station over a period.
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final UUID TARIFF = UUID.randomUUID();

    @Mock
    private GetStationsUseCase stations;
    @Mock
    private GetChargingSessionUseCase sessions;
    @Mock
    private GetPaymentUseCase payments;

    private ReportingService service;
    private final Station station = Station.register("ST-R", "Report Hub", "GB", BigDecimal.ONE, BigDecimal.ONE);

    @BeforeEach
    void setUp() {
        service = new ReportingService(stations, sessions, payments);
    }

    private ChargingSession stopped(String kwh, String cost) {
        return ChargingSession.create(station.id(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, CLOCK)
                .withTariff(TARIFF).start(CLOCK).stop(new BigDecimal(kwh), CLOCK).priced(new BigDecimal(cost), "GBP");
    }

    private static PaymentIntent capturedFor(ChargingSession session) {
        PaymentMethod card = new PaymentMethod(session.userId(), "tok_visa", "visa", "4242", T0);
        return PaymentIntent.authorized(session.id(), session.userId(), "GBP", new BigDecimal("50.00"), card, "ref", CLOCK)
                .captured(session.totalCost(), CLOCK);
    }

    @Test
    @DisplayName("sums energy and money: revenue is captured minus refunds; failed payments are outstanding")
    void shouldSummarise() {
        ChargingSession paid = stopped("20", "9.00");
        ChargingSession refunded = stopped("10", "5.00");
        ChargingSession unpaid = stopped("5", "3.00").paymentFailed(CLOCK);
        ChargingSession active = ChargingSession.create(station.id(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, CLOCK).withTariff(TARIFF).start(CLOCK);
        when(stations.findById(station.id())).thenReturn(station);
        when(sessions.findByStation(any(), any(), any())).thenReturn(List.of(paid, refunded, unpaid, active));
        when(payments.findBySessions(any())).thenReturn(List.of(capturedFor(paid), capturedFor(refunded).refunded(CLOCK)));

        StationReport report = service.report(station.id(), T0, T0.plusSeconds(3600));

        assertEquals("Report Hub", report.stationName());
        assertEquals(4, report.sessions());
        assertEquals(1, report.activeSessions());
        assertEquals(2, report.paidSessions());
        assertEquals(1, report.paymentFailures());
        assertEquals(0, new BigDecimal("35").compareTo(report.energyKwh()));
        assertEquals(new BigDecimal("17.00"), report.billed());
        assertEquals(new BigDecimal("9.00"), report.revenue());
        assertEquals(new BigDecimal("5.00"), report.refunded());
        assertEquals(new BigDecimal("3.00"), report.outstanding());
        assertEquals("GBP", report.currency());
    }

    @Test
    @DisplayName("an empty period reports zeros and no currency")
    void shouldReportEmptyPeriod() {
        when(stations.findById(station.id())).thenReturn(station);
        when(sessions.findByStation(any(), any(), any())).thenReturn(List.of());
        when(payments.findBySessions(any())).thenReturn(List.of());

        StationReport report = service.report(station.id(), T0, T0.plusSeconds(60));

        assertEquals(0, report.sessions());
        assertEquals(BigDecimal.ZERO, report.revenue());
        assertNull(report.currency());
    }

    @Test
    @DisplayName("rejects a period that ends before it starts")
    void shouldRejectBackwardsPeriod() {
        assertThrows(IllegalArgumentException.class, () -> service.report(station.id(), T0, T0));
    }
}
