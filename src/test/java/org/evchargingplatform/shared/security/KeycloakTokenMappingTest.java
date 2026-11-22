package org.evchargingplatform.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for how a Keycloak access token becomes authorities ({@link KeycloakRealmRoleConverter})
 * and a caller ({@link AuthenticatedUser}). Claim names match what Keycloak 26 issues.
 */
class KeycloakTokenMappingTest {

    private static final UUID SUBJECT = UUID.randomUUID();

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(SUBJECT.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    @DisplayName("realm roles become ROLE_ authorities and the principal name is the subject")
    void shouldMapRealmRoles() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("driver", "operator", "offline_access"))));

        AbstractAuthenticationToken auth = new KeycloakRealmRoleConverter().convert(jwt);

        assertEquals(SUBJECT.toString(), auth.getName());
        assertEquals(List.of("ROLE_DRIVER", "ROLE_OPERATOR", "ROLE_OFFLINE_ACCESS"),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    @DisplayName("a token without realm_access has no authorities")
    void shouldHandleMissingRoles() {
        assertTrue(new KeycloakRealmRoleConverter().convert(token(Map.of("email", "a@example.com"))).getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("AuthenticatedUser reads subject, email, name and operator flag")
    void shouldBuildAuthenticatedUser() {
        AuthenticatedUser driver = AuthenticatedUser.from(token(Map.of(
                "email", "alice@example.com", "name", "Alice Smith",
                "realm_access", Map.of("roles", List.of("driver")))));

        assertEquals(SUBJECT, driver.id());
        assertEquals("alice@example.com", driver.email());
        assertEquals("Alice Smith", driver.name());
        assertFalse(driver.operator());
    }

    @Test
    @DisplayName("AuthenticatedUser falls back to preferred_username when there is no name")
    void shouldFallBackToUsername() {
        assertEquals("alice", AuthenticatedUser.from(token(Map.of("preferred_username", "alice"))).name());
    }

    @Test
    @DisplayName("requireOwnerOrOperator allows the owner and operators and refuses others")
    void shouldGuardOwnership() {
        AuthenticatedUser driver = AuthenticatedUser.from(token(Map.of("realm_access", Map.of("roles", List.of("driver")))));
        AuthenticatedUser operator = AuthenticatedUser.from(token(Map.of("realm_access", Map.of("roles", List.of("operator")))));
        UUID someoneElse = UUID.randomUUID();

        assertDoesNotThrow(() -> driver.requireOwnerOrOperator(SUBJECT));
        assertThrows(AccessDeniedException.class, () -> driver.requireOwnerOrOperator(someoneElse));
        assertDoesNotThrow(() -> operator.requireOwnerOrOperator(someoneElse));
    }
}
