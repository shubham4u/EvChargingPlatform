package org.evchargingplatform.station.adapter.in.web;

import org.evchargingplatform.shared.security.SecurityConfig;
import org.evchargingplatform.station.application.port.in.AddConnectorUseCase;
import org.evchargingplatform.station.application.port.in.ConnectorStatus;
import org.evchargingplatform.station.application.port.in.CreateStationUseCase;
import org.evchargingplatform.station.application.port.in.GetConnectorAvailabilityUseCase;
import org.evchargingplatform.station.application.port.in.GetStationsUseCase;
import org.evchargingplatform.station.application.port.in.SearchStationsUseCase;
import org.evchargingplatform.station.application.port.in.StationSearchQuery;
import org.evchargingplatform.station.application.port.in.StationSearchResult;
import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorAvailability;
import org.evchargingplatform.station.domain.ConnectorType;
import org.evchargingplatform.station.domain.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@code GET /stations/search}.
 */
@WebMvcTest(StationController.class)
@Import(SecurityConfig.class)
class StationSearchControllerTest {

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

    private final Station station = Station.register("ST-001", "Euston", "GB", new BigDecimal("51.5282"), new BigDecimal("-0.1337"));
    private final Connector ccs = Connector.add(station.id(), ConnectorType.CCS2, 150, Clock.systemUTC());

    @Test
    @DisplayName("is public and maps every parameter into the query")
    void shouldMapParameters() throws Exception {
        when(searchStationsUseCase.search(any())).thenReturn(List.of(
                new StationSearchResult(station, 0.75, List.of(new ConnectorStatus(ccs, ConnectorAvailability.AVAILABLE)))));

        mockMvc.perform(get("/stations/search")
                        .param("lat", "51.5308").param("lng", "-0.1238").param("radiusKm", "5")
                        .param("connectorType", "CCS2").param("minPowerKw", "100").param("available", "true").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(station.id().toString()))
                .andExpect(jsonPath("$[0].name").value("Euston"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.75))
                .andExpect(jsonPath("$[0].availableConnectors").value(1))
                .andExpect(jsonPath("$[0].connectors[0].connectorType").value("CCS2"))
                .andExpect(jsonPath("$[0].connectors[0].status").value("AVAILABLE"));

        ArgumentCaptor<StationSearchQuery> captor = ArgumentCaptor.forClass(StationSearchQuery.class);
        verify(searchStationsUseCase).search(captor.capture());
        StationSearchQuery query = captor.getValue();
        assertEquals(51.5308, query.latitude());
        assertEquals(-0.1238, query.longitude());
        assertEquals(5, query.radiusKm());
        assertEquals(ConnectorType.CCS2, query.connectorType());
        assertEquals(100, query.minPowerKw());
        assertTrue(query.availableOnly());
        assertEquals(3, query.limit());
    }

    @Test
    @DisplayName("defaults to a 10 km radius, any connector, 20 results")
    void shouldApplyDefaults() throws Exception {
        when(searchStationsUseCase.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/stations/search")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        ArgumentCaptor<StationSearchQuery> captor = ArgumentCaptor.forClass(StationSearchQuery.class);
        verify(searchStationsUseCase).search(captor.capture());
        assertNull(captor.getValue().latitude());
        assertEquals(StationSearchQuery.DEFAULT_RADIUS_KM, captor.getValue().radiusKm());
        assertFalse(captor.getValue().availableOnly());
        assertEquals(StationSearchQuery.DEFAULT_LIMIT, captor.getValue().limit());
    }

    @Test
    @DisplayName("returns 400 problem details for invalid combinations and values")
    void shouldReturn400ForInvalidQuery() throws Exception {
        mockMvc.perform(get("/stations/search").param("lat", "51.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("lat and lng must be given together"));
        mockMvc.perform(get("/stations/search").param("connectorType", "SCHUKO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'SCHUKO' for parameter 'connectorType'"));
        mockMvc.perform(get("/stations/search").param("radiusKm", "500"))
                .andExpect(status().isBadRequest());
        verify(searchStationsUseCase, never()).search(any());
    }
}
