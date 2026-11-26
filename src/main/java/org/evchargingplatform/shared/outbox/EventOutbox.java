package org.evchargingplatform.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Transactional outbox (NFR-3). Publishers call {@link #send} exactly as they used to call
 * {@code KafkaTemplate.send}, but the event is written to {@code outbox_events} in the caller's database
 * transaction. {@link OutboxRelay} publishes it to Kafka once that transaction has committed.
 * <p>
 * Before this, events were sent the moment they were created: a transaction that then rolled back
 * could leave an event announcing something that never happened, and a request blocked if Kafka was
 * down. Now an event exists exactly when its change does, and the API doesn't need Kafka to be up.
 * <p>
 * {@code MANDATORY} makes a publisher called outside a transaction fail loudly instead of silently
 * losing its event. The current trace and span ids are stored so the relay can publish the event as part
 * of the original request's trace.
 */
@Component
public class EventOutbox {

    private final OutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;

    public EventOutbox(OutboxEventJpaRepository repository, ObjectMapper objectMapper, ObjectProvider<Tracer> tracer,
                       Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(String topic, String key, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event.getClass().getSimpleName() + " for the outbox", e);
        }
        Span span = tracer.getIfAvailable() == null ? null : tracer.getIfAvailable().currentSpan();
        repository.save(new OutboxEventEntity(topic, key, event.getClass().getName(), payload,
                span == null ? null : span.context().traceId(), span == null ? null : span.context().spanId(),
                Instant.now(clock)));
    }
}
