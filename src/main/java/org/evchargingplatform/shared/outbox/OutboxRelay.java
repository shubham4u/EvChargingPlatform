package org.evchargingplatform.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes committed outbox events to Kafka, oldest first, and marks them sent.
 * <ul>
 *   <li><b>Order:</b> a send failure stops the batch, so a later event never overtakes an earlier one;
 *   the batch is retried on the next poll.</li>
 *   <li><b>At-least-once:</b> if the app dies between the broker's acknowledgement and the commit, the
 *   event is sent again. Every consumer is idempotent on {@code eventId}.</li>
 *   <li><b>Several instances:</b> rows are claimed with {@code SKIP LOCKED}, so replicas share the work
 *   without double-sending.</li>
 *   <li><b>Tracing:</b> each send runs inside a span parented on the request that created the event, so
 *   the {@code traceparent} on the Kafka record (and the audit's trace id) still leads to that request.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository repository;
    private final OutboxSender sender;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;

    OutboxRelay(OutboxEventJpaRepository repository, OutboxSender sender, ObjectMapper objectMapper,
                ObjectProvider<Tracer> tracer, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.clock = clock;
    }

    /** @return how many events were published */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:200}")
    @Transactional
    public int relay() {
        List<OutboxEventEntity> batch = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        int sent = 0;
        for (OutboxEventEntity event : batch) {
            try {
                publish(event);
                event.published(Instant.now(clock));
                sent++;
            } catch (Exception e) {
                event.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("Outbox event {} ({} on {}) not published yet, attempt {}: {}", event.id(), event.eventType(),
                        event.topic(), event.attempts(), e.getMessage());
                break;
            }
        }
        return sent;
    }

    /** Published rows are only kept for troubleshooting; the audit store is the permanent record. */
    @Scheduled(fixedDelayString = "${app.outbox.purge-ms:3600000}")
    @Transactional
    public int purge() {
        return repository.deletePublishedBefore(Instant.now(clock).minus(Duration.ofDays(7)));
    }

    private void publish(OutboxEventEntity event) throws Exception {
        Object payload = objectMapper.readValue(event.payload(), Class.forName(event.eventType()));
        Tracer t = tracer.getIfAvailable();
        if (t == null || event.traceId() == null) {
            sender.send(event.topic(), event.key(), payload);
            return;
        }
        TraceContext parent = t.traceContextBuilder().traceId(event.traceId()).spanId(event.spanId()).sampled(true).build();
        Span span = t.spanBuilder().setParent(parent).name("outbox publish " + event.topic()).start();
        try (Tracer.SpanInScope ignored = t.withSpan(span)) {
            sender.send(event.topic(), event.key(), payload);
        } finally {
            span.end();
        }
    }
}
