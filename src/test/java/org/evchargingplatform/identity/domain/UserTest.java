package org.evchargingplatform.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link User} aggregate.
 */
class UserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("should register a DRIVER with a normalised email and trimmed name")
    void shouldRegisterDriver() {
        User user = User.register("  Alice@Example.COM ", " Alice ", "+44 7700 900123", CLOCK);

        assertNotNull(user.id());
        assertEquals("alice@example.com", user.email());
        assertEquals("Alice", user.displayName());
        assertEquals("+44 7700 900123", user.phoneNumber());
        assertEquals(UserRole.DRIVER, user.role());
        assertEquals(FIXED_NOW, user.createdAt());
    }

    @Test
    @DisplayName("should allow registering without a phone number")
    void shouldAllowMissingPhone() {
        assertNull(User.register("bob@example.com", "Bob", null, CLOCK).phoneNumber());
    }
}
