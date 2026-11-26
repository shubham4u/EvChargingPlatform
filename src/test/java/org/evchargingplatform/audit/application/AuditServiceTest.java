package org.evchargingplatform.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.audit.application.port.out.AuditEventRepository;
import org.evchargingplatform.audit.domain.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService}: what it extracts from a raw Kafka record, and idempotency.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final String TRACEPARENT = "00-976c803ced483c368ca7663c57e0d8cf-00f067aa0ba902b7-01";

    @Mock
    private AuditEventRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, new ObjectMapper(), CLOCK);
    }

    @Test
    @DisplayName("stores the event with its id, simple type, occurredAt and the trace id from traceparent")
    void shouldRecordEvent() {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"occurredAt\":1795363672.525786629,\"sessionId\":\"s\"}";

        service.record("charging.events.v1", 2, 41L, "session-key",
                "org.evchargingplatform.events.charging.v1.ChargingSessionStartedEvent", TRACEPARENT, payload);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent e = captor.getValue();
        assertEquals(eventId, e.eventId());
        assertEquals("ChargingSessionStartedEvent", e.eventType());
        assertEquals("session-key", e.key());
        assertEquals(Instant.ofEpochSecond(1795363672L, 525786629L), e.occurredAt());
        assertEquals("976c803ced483c368ca7663c57e0d8cf", e.traceId());
        assertEquals(payload, e.payload());
    }

    @Test
    @DisplayName("accepts ISO timestamps and records without a trace")
    void shouldHandleIsoAndNoTrace() {
        service.record("user.events.v1", 0, 1L, "k", null, null,
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-01-01T10:00:00Z\"}");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), captor.getValue().occurredAt());
        assertNull(captor.getValue().traceId());
    }

    @Test
    @DisplayName("a redelivered event is stored once")
    void shouldBeIdempotent() {
        UUID eventId = UUID.randomUUID();
        when(repository.exists(eventId)).thenReturn(true);

        service.record("charging.events.v1", 0, 1L, "k", null, TRACEPARENT, "{\"eventId\":\"" + eventId + "\"}");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an event without an id gets a stable one from its position, so redelivery still dedupes")
    void shouldDeriveStableIdWithoutEventId() {
        service.record("x.events.v1", 1, 7L, null, null, null, "not json");
        service.record("x.events.v1", 1, 7L, null, null, null, "not json");

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(repository, times(2)).exists(ids.capture());
        assertEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
    }

    @Test
    @DisplayName("parses only well-formed W3C traceparent headers")
    void shouldParseTraceparent() {
        assertEquals("976c803ced483c368ca7663c57e0d8cf", AuditService.traceId(TRACEPARENT));
        assertNull(AuditService.traceId("garbage"));
        assertNull(AuditService.traceId(null));
    }

    @Test
    @DisplayName("queries treat blank filters as absent and cap the limit")
    void shouldNormaliseQuery() {
        service.find(" ", "", "abc", 10_000);

        verify(repository).find(eq(null), eq(null), eq("abc"), eq(500));
    }
}
