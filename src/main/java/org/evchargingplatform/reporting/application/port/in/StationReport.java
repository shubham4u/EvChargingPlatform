package org.evchargingplatform.reporting.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What a station did over a period.
 *
 * @param revenue     captured payments, net of refunds
 * @param outstanding the cost of sessions that ended {@code PAYMENT_FAILED}: delivered but not yet paid
 * @param currency    null when nothing was billed (a station's sessions share its tariff currency)
 */
public record StationReport(UUID stationId, String stationName, Instant from, Instant to,
                            long sessions, long activeSessions, long paidSessions, long paymentFailures,
                            BigDecimal energyKwh, BigDecimal billed, BigDecimal revenue, BigDecimal refunded,
                            BigDecimal outstanding, String currency) {
}
