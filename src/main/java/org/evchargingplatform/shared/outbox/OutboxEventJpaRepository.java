package org.evchargingplatform.shared.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * The oldest unpublished events, locked for this relay. {@code SKIP LOCKED} (lock timeout -2) lets
     * several app instances relay in parallel without sending the same row twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEventEntity> findTop100ByPublishedAtIsNullOrderByIdAsc();

    long countByPublishedAtIsNull();

    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
