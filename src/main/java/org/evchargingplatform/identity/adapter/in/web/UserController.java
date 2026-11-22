package org.evchargingplatform.identity.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.evchargingplatform.identity.application.port.in.GetUserUseCase;
import org.evchargingplatform.identity.application.port.in.RegisterUserCommand;
import org.evchargingplatform.identity.application.port.in.RegisterUserUseCase;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    public UserController(RegisterUserUseCase registerUserUseCase, GetUserUseCase getUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.getUserUseCase = getUserUseCase;
    }

    /** Both fields optional: the display name defaults to the name on the account. */
    public record RegisterRequest(
            @Size(max = 100) String displayName,
            @Size(max = 30) String phoneNumber) {
    }

    @PostMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest request) {
        return registerCaller(jwt, request);
    }

    // A body-less POST arrives with whatever Content-Type the client defaults to (Windows PowerShell
    // sends form-urlencoded), which the JSON mapping above would reject with 415. The JSON body must be
    // required there, otherwise Spring matches both mappings for a body-less request and fails as ambiguous.
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerWithoutBody(@AuthenticationPrincipal Jwt jwt) {
        return registerCaller(jwt, null);
    }

    private UserResponse registerCaller(Jwt jwt, RegisterRequest request) {
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        String displayName = request != null && request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName() : caller.name();
        String phoneNumber = request == null ? null : request.phoneNumber();
        return UserResponse.from(registerUserUseCase.register(
                new RegisterUserCommand(caller.id(), caller.email(), displayName == null ? caller.email() : displayName, phoneNumber)));
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
