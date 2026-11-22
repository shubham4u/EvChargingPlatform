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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link ChargingController}.
 * <p>
 * Uses MockMvc with mocked use case interfaces to test
 * HTTP request/response mapping and error handling.
 */
@WebMvcTest(ChargingController.class)
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
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("POST /charging/sessions")
    class StartSession {

        @Test
        @DisplayName("should return 201 Created when request is valid")
        void shouldReturn201WhenValid() throws Exception {
            ChargingSession session = buildSession(ChargingSessionStatus.ACTIVE);
            when(startChargingSessionUseCase.start(any(StartChargingSessionCommand.class))).thenReturn(session);

            mockMvc.perform(post("/charging/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validStartBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(session.id().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                    .andExpect(jsonPath("$.chargingSessionNumber").exists());
        }

        @Test
        @DisplayName("should return 400 Bad Request when connectorId is missing")
        void shouldReturn400WhenConnectorIdMissing() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "stationId", STATION_ID.toString(),
                    "userId", USER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString()));

            mockMvc.perform(post("/charging/sessions")
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
                    "userId", USER_ID.toString(),
                    "vehicleId", VEHICLE_ID.toString(),
                    "reservationId", UUID.randomUUID().toString()));

            mockMvc.perform(post("/charging/sessions")
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
        @DisplayName("should return 200 OK with list of sessions")
        void shouldReturn200WithList() throws Exception {
            when(getChargingSessionUseCase.getAllChargingSessions()).thenReturn(List.of(
                    buildSession(ChargingSessionStatus.ACTIVE), buildSession(ChargingSessionStatus.STOPPED)));

            mockMvc.perform(get("/charging/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /charging/sessions/{id}")
    class FindById {

        @Test
        @DisplayName("should return 200 OK when session exists")
        void shouldReturn200WhenFound() throws Exception {
            ChargingSession session = buildSession(ChargingSessionStatus.ACTIVE);
            when(getChargingSessionUseCase.findById(session.id())).thenReturn(session);

            mockMvc.perform(get("/charging/sessions/{id}", session.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(session.id().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should return 404 Not Found when session does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(getChargingSessionUseCase.findById(id)).thenThrow(new ChargingSessionNotFoundException(id));

            mockMvc.perform(get("/charging/sessions/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Charging session not found"));
        }
    }

    @Nested
    @DisplayName("PATCH /charging/sessions/{id}/stop")
    class Stop {

        @Test
        @DisplayName("should return 200 OK when stop succeeds")
        void shouldReturn200WhenStopped() throws Exception {
            ChargingSession stopped = buildSession(ChargingSessionStatus.STOPPED);
            when(stopChargingSessionUseCase.stop(eq(stopped.id()), any(BigDecimal.class))).thenReturn(stopped);

            mockMvc.perform(patch("/charging/sessions/{id}/stop", stopped.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("STOPPED"))
                    .andExpect(jsonPath("$.totalEnergyKwh").value(12.5));
        }

        @Test
        @DisplayName("should return 400 Bad Request when totalEnergyKwh is missing")
        void shouldReturn400WhenEnergyMissing() throws Exception {
            mockMvc.perform(patch("/charging/sessions/{id}/stop", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 Not Found when session does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(stopChargingSessionUseCase.stop(eq(id), any(BigDecimal.class)))
                    .thenThrow(new ChargingSessionNotFoundException(id));

            mockMvc.perform(patch("/charging/sessions/{id}/stop", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stopBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Charging session not found"));
        }

        @Test
        @DisplayName("should return 409 Conflict when session is not ACTIVE")
        void shouldReturn409WhenNotActive() throws Exception {
            UUID id = UUID.randomUUID();
            when(stopChargingSessionUseCase.stop(eq(id), any(BigDecimal.class)))
                    .thenThrow(new IllegalStateException("Charging session can only be stopped from ACTIVE status"));

            mockMvc.perform(patch("/charging/sessions/{id}/stop", id)
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
                "userId", USER_ID.toString(),
                "vehicleId", VEHICLE_ID.toString()));
    }

    private String stopBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of("totalEnergyKwh", new BigDecimal("12.5")));
    }

    private ChargingSession buildSession(ChargingSessionStatus status) {
        Instant now = Instant.now();
        boolean stopped = status == ChargingSessionStatus.STOPPED;
        return new ChargingSession(
                UUID.randomUUID(),
                "Charging" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                STATION_ID, CONNECTOR_ID, USER_ID, VEHICLE_ID, null,
                status,
                status == ChargingSessionStatus.REQUESTED ? null : now,
                stopped ? now : null,
                stopped ? new BigDecimal("12.5") : null,
                now, now);
    }
}
