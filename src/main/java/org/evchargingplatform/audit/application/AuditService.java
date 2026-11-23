package org.evchargingplatform.audit.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.audit.application.port.in.QueryAuditUseCase;
import org.evchargingplatform.audit.application.port.in.RecordAuditEventUseCase;
import org.evchargingplatform.audit.application.port.out.AuditEventRepository;
import org.evchargingplatform.audit.domain.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit history of every domain event (FR-10), plus the link from each event to the request that
 * caused it (acceptance criterion 5: "operators can trace every major business action from API request
 * to persisted event").
 * <p>
 * It stores the event exactly as published rather than re-modelling it, so the history stays true even
 * as event contracts evolve. The event id comes from the payload; anything without one (it shouldn't
 * happen) gets a stable id from its topic, partition and offset, so redelivery still dedupes.
 */
@Service
@Transactional
public class AuditService implements RecordAuditEventUseCase, QueryAuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_LIMIT = 500;

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(String topic, int partition, long offset, String key, String typeHeader, String traceparent,
            String payload) {
        JsonNode json = parse(payload);
        UUID eventId = json != null && json.hasNonNull("eventId")
                ? UUID.fromString(json.get("eventId").asText())
                : UUID.nameUUIDFromBytes((topic + "/" + partition + "/" + offset).getBytes(StandardCharsets.UTF_8));
        if (repository.exists(eventId)) {
            return;
        }
        repository.save(new AuditEvent(eventId, topic, partition, offset, key, simpleName(typeHeader),
                occurredAt(json), traceId(traceparent), payload, Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> find(String key, String eventType, String traceId, int limit) {
        return repository.find(blankToNull(key), blankToNull(eventType), blankToNull(traceId),
                Math.max(1, Math.min(limit, MAX_LIMIT)));
    }

    private JsonNode parse(String payload) {
        try {
            // BigDecimal, not double: occurredAt is epoch seconds with 9 decimals, more digits than a double holds.
            return objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(payload);
        } catch (Exception e) {
            log.warn("Audit stored an event whose payload isn't JSON");
            return null;
        }
    }

    // Jackson writes Instants as epoch seconds with a fraction; accept ISO strings too.
    private static Instant occurredAt(JsonNode json) {
        JsonNode node = json == null ? null : json.get("occurredAt");
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            BigDecimal seconds = node.decimalValue();
            return Instant.ofEpochSecond(seconds.longValue(), seconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
        }
        return Instant.parse(node.asText());
    }

    // W3C traceparent: version-traceid-spanid-flags
    static String traceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 2 && parts[1].length() == 32 ? parts[1] : null;
    }

    private static String simpleName(String typeHeader) {
        return typeHeader == null ? null : typeHeader.substring(typeHeader.lastIndexOf('.') + 1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
