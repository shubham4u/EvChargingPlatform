package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.CreateStationCommand;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorsUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.domain.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for station creation on {@link StationController}.
 */
@WebMvcTest(StationController.class)
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
    private GetConnectorsUseCase getConnectorsUseCase;

    @Test
    @DisplayName("POST /stations should return 201 when every field is present")
    void shouldReturn201WhenValid() throws Exception {
        Station station = Station.register("ST-001", "Central", "GB", new BigDecimal("51.5074"), new BigDecimal("-0.1278"));
        when(createStationUseCase.create(any(CreateStationCommand.class))).thenReturn(station);

        mockMvc.perform(post("/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalReference\":\"ST-001\",\"name\":\"Central\",\"countryCode\":\"GB\",\"latitude\":51.5074,\"longitude\":-0.1278}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(station.id().toString()));
    }

    // Previously these reached the NOT NULL columns and failed with a 500.
    @ParameterizedTest
    @ValueSource(strings = {"countryCode", "latitude", "longitude"})
    @DisplayName("POST /stations should return 400 when a required field is missing")
    void shouldReturn400WhenFieldMissing(String missing) throws Exception {
        String body = "{\"externalReference\":\"ST-001\",\"name\":\"Central\",\"countryCode\":\"GB\",\"latitude\":51.5074,\"longitude\":-0.1278}"
                .replaceAll(",\"" + missing + "\":[^,}]+", "");

        mockMvc.perform(post("/stations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
