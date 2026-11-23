package org.evchargingplatform.reporting.adapter.in.web;

import org.evchargingplatform.reporting.application.port.in.GetStationReportUseCase;
import org.evchargingplatform.reporting.application.port.in.StationReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Operator reports (operator-only, see SecurityConfig).
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final GetStationReportUseCase reports;
    private final Clock clock;

    public ReportController(GetStationReportUseCase reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    /** Defaults to the last 30 days up to now. {@code from}/{@code to} are ISO-8601 instants. */
    @GetMapping("/stations/{id}")
    public StationReport stationReport(@PathVariable UUID id,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        Instant end = to != null ? to : Instant.now(clock);
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        return reports.report(id, start, end);
    }
}
