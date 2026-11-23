package org.evchargingplatform.charging.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.charging.application.port.in.GetChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionCommand;
import org.evchargingplatform.charging.application.port.in.StartChargingSessionUseCase;
import org.evchargingplatform.charging.application.port.in.StopChargingSessionUseCase;
import org.evchargingplatform.charging.domain.ChargingSession;
import org.evchargingplatform.charging.domain.ChargingSessionStatus;
import org.evchargingplatform.charging.domain.exception.ChargingSessionNotFoundException;
import org.evchargingplatform.charging.domain.exception.ConnectorNotReservedException;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ChargingController}.
 * <p>
 * Uses MockMvc with mocked use case interfaces to test
 * HTTP request/response mapping, error handling, and who may call what.
 */
@WebMvcTest(ChargingController.class)
@Import(SecurityConfig.class)
class ChargingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StartChargingSessionUseCase startChargingSessionUseCase;

    @MockBean
    private StopChargingSessionUseCase stopChargingSessionUseCase;

    @MockBean
    private GetChargingSessionUseCase getChargingSessionUseCase;

    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID CONNECTOR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("POST /charging/sessions")
    class StartSession {

        @Test
        @DisplayName("should return 201 Created and charge as the token's user")
        void shouldReturn201WhenValid() throws Exception {
            ChargingSession session = buildSession(ChargingSessionStatus.ACTIVE, USER_ID);
            when(startChargingSessionUseCase.start(any(StartChargingSessionCommand.class))).thenReturn(session);

            mockMvc.perform(post("/charging/sessions").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validStartBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(session.id().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                    .andExpect(jsonPath("$.chargingSessionNumber").exists());

            ArgumentCaptor<StartChargingSessionCommand> captor = ArgumentCaptor.forClass(StartChargingSessionCommand.class);
            verify(startChargingSessionUseCase).start(captor.capture());
            assertEquals(USER_ID, captor.getValue().userId());
        }

        @Test
        @DisplayName("should ignore a userId in the body and use the token's subject")
        void shouldIgnoreBodyUserId() throws Exception {
            when(startChargingSessionUseCase.start(any(StartChargingSessionCommand.class)))
                    .thenReturn(buildSession(ChargingSessionStatus.ACTIVE, USER_ID));

            mockMvc.perform(post("/charging/sessions").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "stationId", STATION_ID.toString(),
                                    "connectorId", CONNECTOR_ID.toString(),
                                    "userId", OTHER_USER_ID.toString(),
                                    "vehicleId", VEHICLE_ID.toString()))))
                    .andExpect(status().isCreated());

            ArgumentCaptor<StartChargingSessionCommand> captor = ArgumentCaptor.forClass(StartChargingSessionCommand.class);
            verify(startChargingSessionUseCase).start(captor.capture());
            assertEquals(USER_ID, captor.getValue().userId());
        }

        @Test
        @DisplayName("should return 401 without a token")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(post("/charging/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validStartBody()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.title").value("Authentication required"));
            verify(startChargingSessionUseCase, never()).start(any());
        }

        @Test
        @DisplayName("should return 400 Bad Request when connectorId is missing")
        void shouldReturn400WhenConnectorIdMissing() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "stationId", STATION_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString()));

            mockMvc.perform(post("/charging/sessions").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"));
        }

        @Test
        @DisplayName("should return 409 Conflict when the connector is not reserved for the reservation")
        void shouldReturn409WhenConnectorNotReserved() throws Exception {
            when(startChargingSessionUseCase.start(any(StartChargingSessionCommand.class)))
                    .thenThrow(new ConnectorNotReservedException("Connector is not reserved"));

            String body = objectMapper.writeValueAsString(Map.of(
                    "stationId", STATION_ID.toString(),
                    "connectorId", CONNECTOR_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "reservationId", UUID.randomUUID().toString()));

            mockMvc.perform(post("/charging/sessions").with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Connector not reserved"));
        }
    }

    @Nested
    @DisplayName("GET /charging/sessions")
    class FindAll {

        @Test
        @DisplayName("should list only the driver's own sessions")
        void shouldListOwnSessionsForDriver() throws Exception {
            when(getChargingSessionUseCase.findByUser(USER_ID)).thenReturn(List.of(
                    buildSession(ChargingSessionStatus.ACTIVE, USER_ID), buildSession(ChargingSessionStatus.STOPPED, USER_ID)));

            mockMvc.perform(get("/charging/sessions").with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
            verify(getChargingSessionUseCase, never()).getAllChargingSessions();
        }

        @Test
        @DisplayName("should list every session for an operator")
        void shouldListAllForOperator() throws Exception {
            when(getChargingSessionUseCase.getAllChargingSessions()).thenReturn(List.of(
                    buildSession(ChargingSessionStatus.ACTIVE, USER_ID), buildSession(ChargingSessionStatus.ACTIVE, OTHER_USER_ID)));

            mockMvc.perform(get("/charging/sessions").with(operator(UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /charging/sessions/{id}")
    class FindById {

        @Test
        @DisplayName("should return 200 OK for the owner")
        void shouldReturn200WhenFound() throws Exception {
            ChargingSession session = buildSession(ChargingSessionStatus.ACTIVE, USER_ID);
            when(getChargingSessionUseCase.findById(session.id())).thenReturn(session);

            mockMvc.perform(get("/charging/sessions/{id}", session.id()).with(driver(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(session.id().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should return 403 for another driver's session")
        void shouldReturn403ForOtherUsersSession() throws Exception {
            ChargingSession session = buildSession(ChargingSessionStatus.ACTIVE, OTHER_USER_ID);
            when(getChargingSessionUseCase.findById(session.id())).thenReturn(session);

            mockMvc.perform(get("/charging/sessions/{id}", session.id()).with(driver(USER_ID)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }

        @Test
        @DisplayName("should return 404 Not Found when session does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(getChargingSessionUseCase.findById(id)).thenThrow(new ChargingSessionNotFoundException(id));

            mockMvc.perform(get("/charging/sessions/{id}", id).with(driver(USER_ID)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Charging session not found"));
        }
    }

    @Nested
    @DisplayName("PATCH /charging/sessions/{id}/stop")
    class Stop {

        @Test
        @DisplayName("should return 200 OK when the owner stops")
        void shouldReturn200WhenStopped() throws Exception {
            ChargingSession active = buildSession(ChargingSessionStatus.ACTIVE, USER_ID);
            ChargingSession stopped = buildSession(ChargingSessionStatus.STOPPED, USER_ID);
            when(getChargingSessionUseCase.findById(active.id())).thenReturn(active);
            when(stopChargingSessionUseCase.stop(eq(active.id()), any(BigDecimal.class))).thenReturn(stopped);

            mockMvc.perform(patch("/charging/sessions/{id}/stop", active.id()).with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("STOPPED"))
                    .andExpect(jsonPath("$.totalEnergyKwh").value(12.5));
        }

        @Test
        @DisplayName("should let an operator stop any session")
        void shouldLetOperatorStop() throws Exception {
            ChargingSession active = buildSession(ChargingSessionStatus.ACTIVE, OTHER_USER_ID);
            when(getChargingSessionUseCase.findById(active.id())).thenReturn(active);
            when(stopChargingSessionUseCase.stop(eq(active.id()), any(BigDecimal.class)))
                    .thenReturn(buildSession(ChargingSessionStatus.STOPPED, OTHER_USER_ID));

            mockMvc.perform(patch("/charging/sessions/{id}/stop", active.id()).with(operator(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 403 and not stop another driver's session")
        void shouldReturn403ForOtherUsersSession() throws Exception {
            ChargingSession active = buildSession(ChargingSessionStatus.ACTIVE, OTHER_USER_ID);
            when(getChargingSessionUseCase.findById(active.id())).thenReturn(active);

            mockMvc.perform(patch("/charging/sessions/{id}/stop", active.id()).with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isForbidden());
            verify(stopChargingSessionUseCase, never()).stop(any(), any());
        }

        @Test
        @DisplayName("should return 400 Bad Request when totalEnergyKwh is missing")
        void shouldReturn400WhenEnergyMissing() throws Exception {
            mockMvc.perform(patch("/charging/sessions/{id}/stop", UUID.randomUUID()).with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 Not Found when session does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(getChargingSessionUseCase.findById(id)).thenThrow(new ChargingSessionNotFoundException(id));

            mockMvc.perform(patch("/charging/sessions/{id}/stop", id).with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Charging session not found"));
        }

        @Test
        @DisplayName("should return 409 Conflict when session is not ACTIVE")
        void shouldReturn409WhenNotActive() throws Exception {
            ChargingSession stopped = buildSession(ChargingSessionStatus.STOPPED, USER_ID);
            when(getChargingSessionUseCase.findById(stopped.id())).thenReturn(stopped);
            when(stopChargingSessionUseCase.stop(eq(stopped.id()), any(BigDecimal.class)))
                    .thenThrow(new IllegalStateException("Charging session can only be stopped from ACTIVE status"));

            mockMvc.perform(patch("/charging/sessions/{id}/stop", stopped.id()).with(driver(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Invalid state transition"))
                    .andExpect(jsonPath("$.detail").value("Charging session can only be stopped from ACTIVE status"));
        }
    }

    private String validStartBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "stationId", STATION_ID.toString(),
                "connectorId", CONNECTOR_ID.toString(),
                "vehicleId", VEHICLE_ID.toString()));
    }

    private String stopBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of("totalEnergyKwh", new BigDecimal("12.5")));
    }

    private ChargingSession buildSession(ChargingSessionStatus status, UUID userId) {
        Instant now = Instant.now();
        boolean stopped = status == ChargingSessionStatus.STOPPED;
        return new ChargingSession(
                UUID.randomUUID(),
                "Charging" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                STATION_ID, CONNECTOR_ID, userId, VEHICLE_ID, null,
                status,
                status == ChargingSessionStatus.REQUESTED ? null : now,
                stopped ? now : null,
                stopped ? new BigDecimal("12.5") : null,
                UUID.randomUUID(),
                stopped ? new BigDecimal("6.13") : null,
                stopped ? "GBP" : null,
                now, now);
    }
}
