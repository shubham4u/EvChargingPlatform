package org.evchargingplatform.audit.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evchargingplatform.audit.application.port.in.QueryAuditUseCase;
import org.evchargingplatform.audit.domain.AuditEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Operator view of the audit history (operator-only, see SecurityConfig). For example,
 * {@code GET /audit/events?key=<sessionId>} lists everything that happened to one charging session, each
 * with the trace id to open in Jaeger.
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final QueryAuditUseCase audit;
    private final ObjectMapper objectMapper;

    public AuditController(QueryAuditUseCase audit, ObjectMapper objectMapper) {
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public record AuditEventResponse(UUID eventId, String eventType, String topic, String key, Instant occurredAt,
                                     String traceId, JsonNode payload) {
    }

    @GetMapping("/events")
    public List<AuditEventResponse> events(@RequestParam(required = false) String key,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String traceId,
                                           @RequestParam(defaultValue = "100") int limit) {
        return audit.find(key, type, traceId, limit).stream().map(this::toResponse).toList();
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(e.payload());
        } catch (Exception ex) {
            payload = objectMapper.getNodeFactory().textNode(e.payload());
        }
        return new AuditEventResponse(e.eventId(), e.eventType(), e.topic(), e.key(), e.occurredAt(), e.traceId(), payload);
    }
}
