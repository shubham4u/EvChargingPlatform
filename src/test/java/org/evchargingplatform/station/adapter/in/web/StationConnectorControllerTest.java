package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.application.port.in.AddConnectorCommand;
import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.application.port.in.GetConnectorAvailabilityUseCase;
import org.evchargingplatform.station.application.port.in.SearchStationsUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorAvailability;
import org.evchargingplatform.station.domain.ConnectorType;
import org.evchargingplatform.station.domain.exception.StationNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for the connector endpoints on {@link StationController}.
 */
@WebMvcTest(StationController.class)
@Import(SecurityConfig.class)
class StationConnectorControllerTest {

    private static final UUID STATION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateStationUseCase createStationUseCase;

    @MockBean
    private GetStationsUseCase getStationsUseCase;

    @MockBean
    private AddConnectorUseCase addConnectorUseCase;

    @MockBean
    private GetConnectorAvailabilityUseCase connectorAvailabilityUseCase;

    @MockBean
    private SearchStationsUseCase searchStationsUseCase;

    private final Connector connector = Connector.add(STATION_ID, ConnectorType.CCS2, 150, Clock.systemUTC());

    @Test
    @DisplayName("POST /stations/{id}/connectors should return 201 with the connector")
    void shouldReturn201WhenValid() throws Exception {
        when(addConnectorUseCase.addConnector(any(AddConnectorCommand.class))).thenReturn(connector);

        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":150}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(connector.id().toString()))
                .andExpect(jsonPath("$.stationId").value(STATION_ID.toString()))
                .andExpect(jsonPath("$.connectorType").value("CCS2"))
                .andExpect(jsonPath("$.maxPowerKw").value(150))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST /stations/{id}/connectors should return 400 for a missing or invalid power rating")
    void shouldReturn400ForInvalidPower() throws Exception {
        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /stations/{id}/connectors should return 400 for an unknown connector type")
    void shouldReturn400ForUnknownType() throws Exception {
        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"SCHUKO\",\"maxPowerKw\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /stations/{id}/connectors should return 404 for an unknown station")
    void shouldReturn404ForUnknownStation() throws Exception {
        when(addConnectorUseCase.addConnector(any(AddConnectorCommand.class)))
                .thenThrow(new StationNotFoundException(STATION_ID));

        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"TYPE2\",\"maxPowerKw\":22}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Station not found"));
    }

    @Test
    @DisplayName("POST /stations/{id}/connectors should be 403 for a driver")
    void shouldRequireOperator() throws Exception {
        mockMvc.perform(post("/stations/{id}/connectors", STATION_ID).with(driver(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":150}"))
                .andExpect(status().isForbidden());
        verify(addConnectorUseCase, never()).addConnector(any());
    }

    @Test
    @DisplayName("GET /stations/{id}/connectors should return 200 with the station's connectors")
    void shouldListConnectors() throws Exception {
        when(connectorAvailabilityUseCase.findByStation(STATION_ID))
                .thenReturn(List.of(new ConnectorStatus(connector, ConnectorAvailability.OCCUPIED)));

        mockMvc.perform(get("/stations/{id}/connectors", STATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].connectorType").value("CCS2"))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));
    }
}
