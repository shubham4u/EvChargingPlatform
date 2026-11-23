package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.CreateStationCommand;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.application.port.in.GetConnectorAvailabilityUseCase;
import org.evchargingplatform.station.application.port.in.SearchStationsUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.evchargingplatform.station.domain.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for station creation on {@link StationController}.
 */
@WebMvcTest(StationController.class)
@Import(SecurityConfig.class)
class StationControllerTest {

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

    @Test
    @DisplayName("POST /stations should return 201 when every field is present")
    void shouldReturn201WhenValid() throws Exception {
        Station station = Station.register("ST-001", "Central", "GB", new BigDecimal("51.5074"), new BigDecimal("-0.1278"));
        when(createStationUseCase.create(any(CreateStationCommand.class))).thenReturn(station);

        mockMvc.perform(post("/stations").with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalReference\":\"ST-001\",\"name\":\"Central\",\"countryCode\":\"GB\",\"latitude\":51.5074,\"longitude\":-0.1278}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(station.id().toString()));
    }

    @Test
    @DisplayName("POST /stations should be 401 without a token and 403 for a driver")
    void shouldRequireOperator() throws Exception {
        String body = "{\"externalReference\":\"ST-001\",\"name\":\"Central\",\"countryCode\":\"GB\",\"latitude\":51.5074,\"longitude\":-0.1278}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/stations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/stations").with(driver(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verify(createStationUseCase, never()).create(any());
    }

    @Test
    @DisplayName("GET /stations should be public")
    void shouldAllowAnonymousBrowsing() throws Exception {
        when(getStationsUseCase.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/stations"))
                .andExpect(status().isOk());
    }

    // Previously these reached the NOT NULL columns and failed with a 500.
    @ParameterizedTest
    @ValueSource(strings = {"countryCode", "latitude", "longitude"})
    @DisplayName("POST /stations should return 400 when a required field is missing")
    void shouldReturn400WhenFieldMissing(String missing) throws Exception {
        String body = "{\"externalReference\":\"ST-001\",\"name\":\"Central\",\"countryCode\":\"GB\",\"latitude\":51.5074,\"longitude\":-0.1278}"
                .replaceAll(",\"" + missing + "\":[^,}]+", "");

        mockMvc.perform(post("/stations").with(operator(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
