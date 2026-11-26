package org.evchargingplatform.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.evchargingplatform.events.charging.v1.ChargingSessionStoppedEvent;
import org.evchargingplatform.support.TestObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventOutbox} (writing) and {@link OutboxRelay} (publishing in order, retrying).
 */
@ExtendWith(MockitoExtension.class)
class OutboxTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private OutboxEventJpaRepository repository;

    private ChargingSessionStoppedEvent event() {
        return new ChargingSessionStoppedEvent(UUID.randomUUID(), 1, UUID.randomUUID(), null, CLOCK.instant(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CLOCK.instant(),
                new BigDecimal("42.750"));
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Mock
        private Tracer tracer;
        @Mock
        private Span span;
        @Mock
        private TraceContext context;

        @Test
        @DisplayName("stores topic, key, class name, JSON payload and the current trace")
        void shouldStoreEventWithTrace() {
            when(tracer.currentSpan()).thenReturn(span);
            when(span.context()).thenReturn(context);
            when(context.traceId()).thenReturn("976c803ced483c368ca7663c57e0d8cf");
            when(context.spanId()).thenReturn("00f067aa0ba902b7");
            ChargingSessionStoppedEvent event = event();

            new EventOutbox(repository, objectMapper, TestObjectProvider.of(tracer), CLOCK)
                    .send("charging.events.v1", event.sessionId().toString(), event);

            ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            verify(repository).save(captor.capture());
            OutboxEventEntity row = captor.getValue();
            assertEquals("charging.events.v1", row.topic());
            assertEquals(event.sessionId().toString(), row.key());
            assertEquals(ChargingSessionStoppedEvent.class.getName(), row.eventType());
            assertTrue(row.payload().contains("\"totalEnergyKwh\":42.750"));
            assertEquals("976c803ced483c368ca7663c57e0d8cf", row.traceId());
            assertEquals("00f067aa0ba902b7", row.spanId());
            assertNull(row.publishedAt());
        }

        @Test
        @DisplayName("works without tracing")
        void shouldStoreWithoutTrace() {
            new EventOutbox(repository, objectMapper, TestObjectProvider.empty(), CLOCK).send("t", "k", event());

            ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().traceId());
        }
    }

    @Nested
    @DisplayName("relaying")
    class Relaying {

        private final List<Object> delivered = new ArrayList<>();

        private OutboxEventEntity row(ChargingSessionStoppedEvent e) throws Exception {
            return new OutboxEventEntity("charging.events.v1", e.sessionId().toString(), e.getClass().getName(),
                    objectMapper.writeValueAsString(e), null, null, CLOCK.instant());
        }

        @Test
        @DisplayName("publishes the batch in order, as the original record types, and marks each published")
        void shouldPublishInOrder() throws Exception {
            ChargingSessionStoppedEvent first = event();
            ChargingSessionStoppedEvent second = event();
            OutboxEventEntity a = row(first);
            OutboxEventEntity b = row(second);
            when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(a, b));
            OutboxRelay relay = new OutboxRelay(repository, (topic, key, event) -> delivered.add(event), objectMapper,
                    TestObjectProvider.empty(), CLOCK);

            assertEquals(2, relay.relay());

            assertEquals(List.of(first, second), delivered);   // round-trips to equal records
            assertEquals(CLOCK.instant(), a.publishedAt());
            assertEquals(1, b.attempts());
        }

        @Test
        @DisplayName("a send failure stops the batch so nothing overtakes it, and is retried next time")
        void shouldStopAtFailure() throws Exception {
            OutboxEventEntity a = row(event());
            OutboxEventEntity b = row(event());
            when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(a, b));
            OutboxRelay relay = new OutboxRelay(repository, (topic, key, event) -> {
                throw new IllegalStateException("broker unavailable");
            }, objectMapper, TestObjectProvider.empty(), CLOCK);

            assertEquals(0, relay.relay());

            assertNull(a.publishedAt());
            assertEquals(1, a.attempts());
            assertTrue(a.lastError().contains("broker unavailable"));
            assertEquals(0, b.attempts(), "the later event wasn't even tried");
        }
    }
}
