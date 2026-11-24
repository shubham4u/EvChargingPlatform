package org.evchargingplatform.identity.adapter.in.web;

import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.in.RegisterUserUseCase;
import org.evchargingplatform.identity.domain.User;
import org.evchargingplatform.identity.domain.exception.UserAlreadyRegisteredException;
import org.evchargingplatform.identity.domain.exception.UserNotFoundException;
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

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link UserController}.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @MockBean
    private GetUserUseCase getUserUseCase;

    private final User user = User.register(USER_ID, "alice@example.com", "Alice", null, Clock.systemUTC());

    @Test
    @DisplayName("POST /users/me should register a profile from the token's subject and email")
    void shouldRegisterFromToken() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class))).thenReturn(user);

        mockMvc.perform(post("/users/me").with(driver(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+44 7700 900123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("DRIVER"));

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).register(captor.capture());
        assertEquals(USER_ID, captor.getValue().userId());
        assertEquals("user-" + USER_ID + "@example.com", captor.getValue().email());
        assertEquals("Test User", captor.getValue().displayName(), "defaults to the token's name");
        assertEquals("+44 7700 900123", captor.getValue().phoneNumber());
    }

    @Test
    @DisplayName("POST /users/me should accept an empty body")
    void shouldAcceptEmptyBody() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class))).thenReturn(user);

        mockMvc.perform(post("/users/me").with(driver(USER_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /users/me without a body should work whatever Content-Type the client sends")
    void shouldAcceptBodylessPostWithFormContentType() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class))).thenReturn(user);

        // What Windows PowerShell's Invoke-RestMethod -Method Post sends when there is no -Body
        mockMvc.perform(post("/users/me").with(driver(USER_ID)).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isCreated());
        // What most HTTP clients send once the JSON header is set but there's nothing to post
        mockMvc.perform(post("/users/me").with(driver(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/users/me").with(driver(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /users/me should use a displayName from the body over the token's name")
    void shouldUseBodyDisplayName() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class))).thenReturn(user);

        mockMvc.perform(post("/users/me").with(driver(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ali\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).register(captor.capture());
        assertEquals("Ali", captor.getValue().displayName());
    }

    @Test
    @DisplayName("POST /users/me should return 400 for a body that isn't valid JSON or breaks a size limit")
    void shouldReturn400ForBadBody() throws Exception {
        mockMvc.perform(post("/users/me").with(driver(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        mockMvc.perform(post("/users/me").with(driver(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"" + "9".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("phoneNumber")));
        verify(registerUserUseCase, org.mockito.Mockito.never()).register(any());
    }

    @Test
    @DisplayName("POST /users/me should return 409 when the account already has a profile")
    void shouldReturn409WhenAlreadyRegistered() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class)))
                .thenThrow(new UserAlreadyRegisteredException(USER_ID));

        mockMvc.perform(post("/users/me").with(driver(USER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("User already registered"));
    }

    @Test
    @DisplayName("POST /users/me should return 401 without a token")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/me should return the caller's profile, or 404 before registering")
    void shouldReturnOwnProfile() throws Exception {
        UUID unregistered = UUID.randomUUID();
        when(getUserUseCase.findById(USER_ID)).thenReturn(user);
        when(getUserUseCase.findById(unregistered)).thenThrow(new UserNotFoundException(unregistered));

        mockMvc.perform(get("/users/me").with(driver(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
        mockMvc.perform(get("/users/me").with(driver(unregistered)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    @DisplayName("GET /users/{id} should allow self and operators, and refuse other drivers")
    void shouldGuardGetById() throws Exception {
        when(getUserUseCase.findById(USER_ID)).thenReturn(user);

        mockMvc.perform(get("/users/{id}", USER_ID).with(driver(USER_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", USER_ID).with(operator(UUID.randomUUID()))).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", USER_ID).with(driver(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /users should be operator-only")
    void shouldRestrictListToOperators() throws Exception {
        when(getUserUseCase.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/users").with(operator(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/users").with(driver(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Your account does not have the role this endpoint requires"));
    }
}
