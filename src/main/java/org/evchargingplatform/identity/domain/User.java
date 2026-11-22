package org.evchargingplatform.identity.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * User aggregate: a registered driver.
 * <p>
 * Credentials live in Keycloak. This aggregate is the driver's profile: its {@code id} is the
 * Keycloak subject ({@code sub}) of the account that registered it, so the access token and the
 * profile always agree on who the user is. Email is normalised to lower case so uniqueness does
 * not depend on how the address was typed.
 */
public record User(UUID id, String email, String displayName, String phoneNumber, UserRole role,
        Instant createdAt) implements Serializable {

    public static User register(UUID id, String email, String displayName, String phoneNumber, Clock clock) {
        return new User(id, normaliseEmail(email), displayName.trim(), phoneNumber,
                UserRole.DRIVER, Instant.now(clock));
    }

    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
