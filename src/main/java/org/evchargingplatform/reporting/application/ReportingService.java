package org.evchargingplatform.reporting.application;

import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.evchargingplatform.payments.application.port.in.GetPaymentUseCase;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentStatus;
import org.evchargingplatform.reporting.application.port.in.GetStationReportUseCase;
import org.evchargingplatform.reporting.application.port.in.StationReport;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.domain.Station;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Operator reporting (FR-10): per-station usage and money over a period.
 * <p>
 * It reads the owning modules through their query use cases instead of keeping its own copy. That is
 * correct to the second and fine at this scale. When reports get heavy, the next step is an analytics read
 * model fed by the same events the audit store already captures.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService implements GetStationReportUseCase {

    private final GetStationsUseCase stations;
    private final GetChargingSessionUseCase sessions;
    private final GetPaymentUseCase payments;

    public ReportingService(GetStationsUseCase stations, GetChargingSessionUseCase sessions, GetPaymentUseCase payments) {
        this.stations = stations;
        this.sessions = sessions;
        this.payments = payments;
    }

    @Override
    public StationReport report(java.util.UUID stationId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        Station station = stations.findById(stationId);
        List<ChargingSession> inPeriod = sessions.findByStation(stationId, from, to);
        List<PaymentIntent> paid = payments.findBySessions(inPeriod.stream().map(ChargingSession::id).toList());

        List<ChargingSession> failed = withStatus(inPeriod, ChargingSessionStatus.PAYMENT_FAILED);
        String currency = inPeriod.stream().map(ChargingSession::currency).filter(Objects::nonNull).findFirst().orElse(null);

        BigDecimal captured = sum(paid.stream().filter(p -> p.status() == PaymentStatus.CAPTURED || p.status() == PaymentStatus.REFUNDED).toList(),
                PaymentIntent::capturedAmount);
        BigDecimal refunded = sum(paid.stream().filter(p -> p.status() == PaymentStatus.REFUNDED).toList(), PaymentIntent::capturedAmount);

        return new StationReport(stationId, station.name(), from, to,
                inPeriod.size(),
                withStatus(inPeriod, ChargingSessionStatus.ACTIVE).size(),
                paid.stream().filter(p -> p.status() == PaymentStatus.CAPTURED || p.status() == PaymentStatus.REFUNDED).count(),
                failed.size(),
                sum(inPeriod, ChargingSession::totalEnergyKwh),
                sum(inPeriod, ChargingSession::totalCost),
                captured.subtract(refunded),
                refunded,
                sum(failed, ChargingSession::totalCost),
                currency);
    }

    private static List<ChargingSession> withStatus(List<ChargingSession> all, ChargingSessionStatus status) {
        return all.stream().filter(s -> s.status() == status).toList();
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> amount) {
        return items.stream().map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
