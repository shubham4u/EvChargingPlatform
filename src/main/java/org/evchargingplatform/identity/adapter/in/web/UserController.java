package org.evchargingplatform.identity.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.in.RegisterUserUseCase;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST adapter for the identity bounded context.
 * <p>
 * Accounts and passwords are managed by Keycloak. After signing up there, a driver calls
 * {@code POST /users/me} once to create their profile; its id and email come from the token.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public UserController(RegisterUserUseCase registerUserUseCase, GetUserUseCase getUserUseCase,
                          ObjectMapper objectMapper, Validator validator) {
        this.registerUserUseCase = registerUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** Both fields optional: the display name defaults to the name on the account. */
    public record RegisterRequest(
            @Size(max = 100) String displayName,
            @Size(max = 30) String phoneNumber) {
    }

    /**
     * The body is optional, and clients send a body-less POST in different ways: no Content-Type,
     * form-urlencoded (Windows PowerShell's default), or {@code application/json} with nothing after it
     * (most HTTP libraries once the header is set). Spring's JSON binding rejects some of these with
     * 415 or 400, and two mappings split by {@code consumes} can't cover them all without becoming
     * ambiguous. So this reads the raw body, which works for any Content-Type, and parses it as JSON
     * only when there is one.
     */
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) String body) {
        RegisterRequest request = parse(body);
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        String displayName = request != null && request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName() : caller.name();
        String phoneNumber = request == null ? null : request.phoneNumber();
        return UserResponse.from(registerUserUseCase.register(
                new RegisterUserCommand(caller.id(), caller.email(), displayName == null ? caller.email() : displayName, phoneNumber)));
    }

    private RegisterRequest parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        RegisterRequest request;
        try {
            request = objectMapper.readValue(body, RegisterRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Body must be JSON like {\"displayName\": \"...\", \"phoneNumber\": \"...\"}, or empty");
        }
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<RegisterRequest> first = violations.iterator().next();
            throw new IllegalArgumentException(first.getPropertyPath() + " " + first.getMessage());
        }
        return request;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(getUserUseCase.findById(AuthenticatedUser.from(jwt).id()));
    }

    // Operator only (see SecurityConfig)
    @GetMapping
    public List<UserResponse> findAll() {
        return getUserUseCase.findAll().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AuthenticatedUser.from(jwt).requireOwnerOrOperator(id);
        return UserResponse.from(getUserUseCase.findById(id));
    }
}
