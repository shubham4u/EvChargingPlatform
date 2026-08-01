# Sprint 2 — Station Event Publishing

Sprint 2 extends the completed Station Service without adding consumers or a Reservation Service. The application depends on the outbound `DomainEventPublisher` port; Kafka is an infrastructure adapter.

## Current implementation

- Immutable versioned `StationCreatedEvent` record with UUID and `Instant` metadata.
- Existing station registration publication remains supported.
- Asynchronous `KafkaTemplate` publication with success/failure logging.
- External topic names: `station.events`, `charger.events`, and `telemetry.events`.
- Kafka producer defaults: `acks=all`, idempotence enabled, LZ4 compression, configurable retries/batching.
- Kafka topic beans create the topics when broker auto-creation is unavailable.

The current domain exposes station creation, so that flow is wired first. Charger and telemetry event records should be added as those Sprint 1 use cases are exposed; no consumers are introduced.

## Why this remains hexagonal and DDD-aligned

The application service sees only an outbound port and publishes after persistence succeeds. Kafka configuration, serialization, topic provisioning, and failure logging stay in infrastructure. Events contain business data and identifiers rather than JPA entities, preserving aggregate boundaries and allowing Sprint 3 Reservation Service to consume the station contract independently.

## Configuration

Set `KAFKA_BOOTSTRAP_SERVERS`, `STATION_EVENTS_TOPIC`, `CHARGER_EVENTS_TOPIC`, and `TELEMETRY_EVENTS_TOPIC` through the environment. Kafka can be disabled locally with `KAFKA_ENABLED=false`.
