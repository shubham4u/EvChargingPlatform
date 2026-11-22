package org.evchargingplatform.support;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * MockMvc request post-processors that authenticate as a Keycloak user. The claims mirror a
 * real Keycloak access token ({@code sub}, {@code email}, {@code name}, {@code realm_access.roles}),
 * and the authorities match what {@code KeycloakRealmRoleConverter} would produce.
 */
public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor driver(UUID userId) {
        return user(userId, "driver");
    }

    public static JwtRequestPostProcessor operator(UUID userId) {
        return user(userId, "driver", "operator");
    }

    private static JwtRequestPostProcessor user(UUID userId, String... roles) {
        return jwt()
                .jwt(token -> token
                        .subject(userId.toString())
                        .claim("email", "user-" + userId + "@example.com")
                        .claim("name", "Test User")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .toList());
    }
}
