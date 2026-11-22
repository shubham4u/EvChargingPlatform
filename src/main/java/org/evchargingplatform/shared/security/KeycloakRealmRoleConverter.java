package org.evchargingplatform.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns Keycloak realm roles ({@code realm_access.roles}) into {@code ROLE_*} authorities,
 * so {@code driver} becomes {@code ROLE_DRIVER} and {@code operator} becomes {@code ROLE_OPERATOR}.
 * <p>
 * Spring's default converter only reads the {@code scope} claim, which Keycloak uses for OAuth
 * scopes rather than application roles.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase(Locale.ROOT)))
                .toList();
    }
}
