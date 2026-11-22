package org.evchargingplatform.identity.adapter.out.persistence;

import org.evchargingplatform.identity.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link UserJpaAdapter} against the real Flyway schema on H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import(UserJpaAdapter.class)
class UserJpaAdapterTest {

    @Autowired
    private UserJpaAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should round-trip a user and find it by email")
    void shouldRoundTripUser() {
        User user = User.register("alice@example.com", "Alice", "+44 7700 900123", Clock.systemUTC());

        adapter.save(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = adapter.findById(user.id()).orElseThrow();
        assertEquals(user.email(), loaded.email());
        assertEquals(user.role(), loaded.role());
        assertTrue(adapter.existsByEmail("alice@example.com"));
        assertFalse(adapter.existsByEmail("bob@example.com"));
    }
}
