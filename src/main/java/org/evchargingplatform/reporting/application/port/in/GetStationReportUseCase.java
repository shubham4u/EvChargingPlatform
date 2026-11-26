package org.evchargingplatform.reporting.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface GetStationReportUseCase {
    /** Covers sessions created in {@code [from, to)}. */
    StationReport report(UUID stationId, Instant from, Instant to);
}
