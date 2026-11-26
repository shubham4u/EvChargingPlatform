package org.evchargingplatform.shared.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for the outbox table (V13) on H2: the locking poll query runs, keeps insertion order,
 * skips published rows, and the purge only removes old published ones.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
class OutboxPersistenceTest {

    @Autowired
    private OutboxEventJpaRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    private OutboxEventEntity row(String key) {
        return repository.save(new OutboxEventEntity("t.events.v1", key, "x.Type", "{}", null, null, Instant.now()));
    }

    @Test
    @DisplayName("the relay's poll returns unpublished rows in insertion order")
    void shouldPollInOrder() {
        OutboxEventEntity a = row("a");
        OutboxEventEntity b = row("b");
        OutboxEventEntity c = row("c");
        b.published(Instant.now());
        entityManager.flush();
        entityManager.clear();

        List<OutboxEventEntity> batch = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();

        assertEquals(List.of("a", "c"), batch.stream().map(OutboxEventEntity::key).toList());
        assertTrue(a.id() < c.id());
        assertEquals(2, repository.countByPublishedAtIsNull());
    }

    @Test
    @DisplayName("purge removes only rows published before the cut-off")
    void shouldPurgeOldPublishedRows() {
        OutboxEventEntity old = row("old");
        old.published(Instant.now().minus(Duration.ofDays(8)));
        OutboxEventEntity recent = row("recent");
        recent.published(Instant.now());
        row("pending");
        entityManager.flush();

        assertEquals(1, repository.deletePublishedBefore(Instant.now().minus(Duration.ofDays(7))));
        entityManager.clear();
        assertEquals(2, repository.count());
    }
}
