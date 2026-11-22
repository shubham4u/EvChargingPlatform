package org.evchargingplatform.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The caller of a request, read from the validated access token.
 * <p>
 * {@link #id()} is the token's {@code sub}, which Keycloak issues as a UUID and which is also the
 * id of the caller's {@code User} profile. Web adapters use this instead of trusting a
 * {@code userId} in the request body.
 */
public record AuthenticatedUser(UUID id, String email, String name, boolean operator) {

    public static AuthenticatedUser from(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), name,
                hasRealmRole(jwt, "operator"));
    }

    /**
     * Allows the call when the caller owns the resource or is an operator.
     *
     * @throws AccessDeniedException otherwise (mapped to 403)
     */
    public void requireOwnerOrOperator(UUID ownerId) {
        if (!operator && !id.equals(ownerId)) {
            throw new AccessDeniedException("You can only access your own resources");
        }
    }

    private static boolean hasRealmRole(Jwt jwt, String role) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        return realmAccess != null
                && realmAccess.get("roles") instanceof Collection<?> roles
                && roles.contains(role);
    }
}
