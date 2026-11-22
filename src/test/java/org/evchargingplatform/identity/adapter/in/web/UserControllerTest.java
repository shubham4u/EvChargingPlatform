package org.evchargingplatform.identity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.in.RegisterUserUseCase;
import org.evchargingplatform.identity.domain.User;
import org.evchargingplatform.identity.domain.exception.DuplicateUserException;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link UserController}.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @MockBean
    private GetUserUseCase getUserUseCase;

    private final User user = User.register("alice@example.com", "Alice", null, Clock.systemUTC());

    @Test
    @DisplayName("POST /users should return 201 with the registered user")
    void shouldReturn201WhenValid() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class))).thenReturn(user);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "alice@example.com", "displayName", "Alice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(user.id().toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    @Test
    @DisplayName("POST /users should return 400 for an invalid email")
    void shouldReturn400ForInvalidEmail() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email", "displayName", "Alice"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /users should return 409 for a duplicate email")
    void shouldReturn409ForDuplicate() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                .thenThrow(new DuplicateUserException("alice@example.com"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "alice@example.com", "displayName", "Alice"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("User already exists"));
    }

    @Test
    @DisplayName("GET /users/{id} should return 200, and 404 when unknown")
    void shouldFindById() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(getUserUseCase.findById(user.id())).thenReturn(user);
        when(getUserUseCase.findById(unknown)).thenThrow(new UserNotFoundException(unknown));

        mockMvc.perform(get("/users/{id}", user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice"));
        mockMvc.perform(get("/users/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    @DisplayName("GET /users should return 200 with the list")
    void shouldListUsers() throws Exception {
        when(getUserUseCase.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
