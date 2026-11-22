package org.evchargingplatform.vehicle.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
import org.evchargingplatform.vehicle.application.port.in.GetVehicleUseCase;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleCommand;
import org.evchargingplatform.vehicle.application.port.in.RegisterVehicleUseCase;
import org.evchargingplatform.vehicle.domain.Vehicle;
import org.evchargingplatform.vehicle.domain.exception.DuplicateVehicleException;
import org.evchargingplatform.vehicle.domain.exception.VehicleNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link VehicleController}.
 */
@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String VIN = "5YJ3E1EA7KF317000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterVehicleUseCase registerVehicleUseCase;

    @MockBean
    private GetVehicleUseCase getVehicleUseCase;

    private final Vehicle vehicle = Vehicle.register(OWNER_ID, VIN, "Tesla", "Model 3", Clock.systemUTC());

    private String body(String vin) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "ownerUserId", OWNER_ID.toString(), "vin", vin, "make", "Tesla", "model", "Model 3"));
    }

    @Test
    @DisplayName("POST /vehicles should return 201 with the registered vehicle")
    void shouldReturn201WhenValid() throws Exception {
        when(registerVehicleUseCase.register(any(RegisterVehicleCommand.class))).thenReturn(vehicle);

        mockMvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body(VIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(vehicle.id().toString()))
                .andExpect(jsonPath("$.ownerUserId").value(OWNER_ID.toString()))
                .andExpect(jsonPath("$.vin").value(VIN));
    }

    @Test
    @DisplayName("POST /vehicles should return 400 when the VIN is the wrong length")
    void shouldReturn400ForShortVin() throws Exception {
        mockMvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body("ABC123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /vehicles should return 404 when the owner does not exist")
    void shouldReturn404ForUnknownOwner() throws Exception {
        when(registerVehicleUseCase.register(any(RegisterVehicleCommand.class)))
                .thenThrow(new UserNotFoundException(OWNER_ID));

        mockMvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body(VIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    @DisplayName("POST /vehicles should return 409 for a duplicate VIN")
    void shouldReturn409ForDuplicateVin() throws Exception {
        when(registerVehicleUseCase.register(any(RegisterVehicleCommand.class)))
                .thenThrow(new DuplicateVehicleException(VIN));

        mockMvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body(VIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Vehicle already registered"));
    }

    @Test
    @DisplayName("GET /vehicles?ownerUserId= should list that user's vehicles")
    void shouldListByOwner() throws Exception {
        when(getVehicleUseCase.findByOwner(OWNER_ID)).thenReturn(List.of(vehicle));

        mockMvc.perform(get("/vehicles").param("ownerUserId", OWNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(getVehicleUseCase).findByOwner(OWNER_ID);
    }

    @Test
    @DisplayName("GET /vehicles/{id} should return 200, and 404 when unknown")
    void shouldFindById() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(getVehicleUseCase.findById(vehicle.id())).thenReturn(vehicle);
        when(getVehicleUseCase.findById(unknown)).thenThrow(new VehicleNotFoundException(unknown));

        mockMvc.perform(get("/vehicles/{id}", vehicle.id())).andExpect(status().isOk());
        mockMvc.perform(get("/vehicles/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Vehicle not found"));
    }
}
