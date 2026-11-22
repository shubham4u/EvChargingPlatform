package org.evchargingplatform.identity.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * User aggregate: a registered driver.
 * <p>
 * There is no authentication yet. Callers pass {@code userId} explicitly, and this aggregate
 * is the record other contexts check that id against. Email is normalised to lower case so
 * uniqueness does not depend on how the address was typed.
 */
public record User(UUID id, String email, String displayName, String phoneNumber, UserRole role,
        Instant createdAt) implements Serializable {

    public static User register(String email, String displayName, String phoneNumber, Clock clock) {
        return new User(UUID.randomUUID(), normaliseEmail(email), displayName.trim(), phoneNumber,
                UserRole.DRIVER, Instant.now(clock));
    }

    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
