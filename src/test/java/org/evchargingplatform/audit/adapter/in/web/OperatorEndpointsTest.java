package org.evchargingplatform.audit.adapter.in.web;

import org.evchargingplatform.audit.application.port.in.QueryAuditUseCase;
import org.evchargingplatform.audit.domain.AuditEvent;
import org.evchargingplatform.reporting.adapter.in.web.ReportController;
import org.evchargingplatform.reporting.application.port.in.GetStationReportUseCase;
import org.evchargingplatform.reporting.application.port.in.StationReport;
import org.evchargingplatform.shared.config.ClockConfig;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for the operator-only audit and report endpoints.
 */
@WebMvcTest({AuditController.class, ReportController.class})
@Import({SecurityConfig.class, ClockConfig.class})
class OperatorEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryAuditUseCase audit;
    @MockBean
    private GetStationReportUseCase reports;

    @Test
    @DisplayName("audit returns events with their parsed payload and trace id, for operators only")
    void shouldServeAuditToOperators() throws Exception {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), "charging.events.v1", 0, 3, "session-1",
                "ChargingSessionStartedEvent", Instant.parse("2026-01-01T12:00:00Z"), "976c803ced483c368ca7663c57e0d8cf",
                "{\"sessionId\":\"session-1\"}", Instant.now());
        when(audit.find(eq("session-1"), any(), any(), eq(100))).thenReturn(List.of(event));

        mockMvc.perform(get("/audit/events").param("key", "session-1").with(operator(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ChargingSessionStartedEvent"))
                .andExpect(jsonPath("$[0].traceId").value("976c803ced483c368ca7663c57e0d8cf"))
                .andExpect(jsonPath("$[0].payload.sessionId").value("session-1"));
        mockMvc.perform(get("/audit/events").with(driver(UUID.randomUUID()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/audit/events")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("station report defaults to the last 30 days, for operators only")
    void shouldServeReportsToOperators() throws Exception {
        UUID stationId = UUID.randomUUID();
        when(reports.report(eq(stationId), any(), any())).thenAnswer(invocation -> new StationReport(stationId, "Hub",
                invocation.getArgument(1), invocation.getArgument(2), 1, 0, 1, 0, BigDecimal.TEN, new BigDecimal("5.00"),
                new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, "GBP"));

        mockMvc.perform(get("/reports/stations/{id}", stationId).with(operator(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(5.00))
                .andExpect(jsonPath("$.currency").value("GBP"));
        mockMvc.perform(get("/reports/stations/{id}", stationId).with(driver(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(reports, times(1)).report(eq(stationId), any(), any());
    }
}
