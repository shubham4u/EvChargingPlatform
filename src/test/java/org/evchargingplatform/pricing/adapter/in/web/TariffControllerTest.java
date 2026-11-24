package org.evchargingplatform.pricing.adapter.in.web;

import org.evchargingplatform.pricing.application.port.in.GetTariffUseCase;
import org.evchargingplatform.pricing.application.port.in.SetTariffCommand;
import org.evchargingplatform.pricing.application.port.in.SetTariffUseCase;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.pricing.domain.exception.TariffNotFoundException;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link TariffController}.
 */
@WebMvcTest(TariffController.class)
@Import(SecurityConfig.class)
class TariffControllerTest {

    private static final UUID STATION = UUID.randomUUID();
    private static final String BODY = "{\"currency\":\"GBP\",\"pricePerKwh\":0.45,\"pricePerMinute\":0.05,\"sessionFee\":0.50}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SetTariffUseCase setTariffUseCase;
    @MockBean
    private GetTariffUseCase getTariffUseCase;

    private final Tariff tariff = Tariff.define(STATION, "GBP", new BigDecimal("0.45"), new BigDecimal("0.05"),
            new BigDecimal("0.50"), UUID.randomUUID(), Clock.systemUTC());

    @Test
    @DisplayName("GET is public; 404 explains how to set a tariff")
    void shouldReadPublicly() throws Exception {
        UUID unpriced = UUID.randomUUID();
        when(getTariffUseCase.currentFor(STATION)).thenReturn(tariff);
        when(getTariffUseCase.currentFor(unpriced)).thenThrow(TariffNotFoundException.forStation(unpriced));

        mockMvc.perform(get("/stations/{id}/tariff", STATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.pricePerKwh").value(0.45));
        mockMvc.perform(get("/stations/{id}/tariff", unpriced))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Tariff not found"));
    }

    @Test
    @DisplayName("PUT as an operator sets the tariff and records who did it")
    void shouldSetAsOperator() throws Exception {
        UUID operatorId = UUID.randomUUID();
        when(setTariffUseCase.setTariff(any())).thenReturn(tariff);

        mockMvc.perform(put("/stations/{id}/tariff", STATION).with(operator(operatorId))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tariff.id().toString()));

        ArgumentCaptor<SetTariffCommand> captor = ArgumentCaptor.forClass(SetTariffCommand.class);
        verify(setTariffUseCase).setTariff(captor.capture());
        assertEquals(STATION, captor.getValue().stationId());
        assertEquals(operatorId, captor.getValue().operatorId());
    }

    @Test
    @DisplayName("PUT is 401 anonymously, 403 for a driver, 400 for a missing price")
    void shouldGuardSet() throws Exception {
        mockMvc.perform(put("/stations/{id}/tariff", STATION).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/stations/{id}/tariff", STATION).with(driver(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/stations/{id}/tariff", STATION).with(operator(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"GBP\",\"pricePerKwh\":0.45}"))
                .andExpect(status().isBadRequest());
        verify(setTariffUseCase, never()).setTariff(any());
    }
}
