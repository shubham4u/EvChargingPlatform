# Reservation Service — Sprint 3 Design

This document defines the independent Reservation Service requested for Sprint 3. It does not modify the Station Service, make synchronous calls to it, implement Charging or Payment, or add Kafka consumers.

## Ownership and boundaries

The service owns the Reservation aggregate, its lifecycle, expiration, persistence, REST API, and reservation events. Station, charger, user, and vehicle references are stored as IDs only; their state is not replicated through synchronous REST calls.

```mermaid
flowchart LR
    Driver[Driver / Client] --> API[Reservation REST API]
    API --> App[Reservation Application Services]
    App --> Aggregate[Reservation Aggregate]
    Aggregate --> Repo[ReservationRepository Port]
    Repo --> DB[(Reservation PostgreSQL Database)]
    App --> Publisher[ReservationEventPublisher Port]
    Publisher --> Kafka[(reservation.events)]
    Scheduler[Expiration Scheduler] --> App
```

## Aggregate and lifecycle

`Reservation` is the aggregate root. It owns `create`, `activate`, `cancel`, `complete`, `expire`, and `isExpired` rules. The valid terminal states are `CANCELLED`, `EXPIRED`, and `COMPLETED`; terminal reservations cannot be modified. The default reservation period is ten minutes.

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> ACTIVE: activate
    CREATED --> CANCELLED: cancel
    ACTIVE --> CANCELLED: cancel
    ACTIVE --> COMPLETED: complete
    CREATED --> EXPIRED: expiration reached
    ACTIVE --> EXPIRED: expiration reached
    CANCELLED --> [*]
    COMPLETED --> [*]
    EXPIRED --> [*]
```

## API contract

| Method | Path | Purpose |
|---|---|---|
| POST | `/reservations` | Create a reservation |
| GET | `/reservations/{id}` | Retrieve one reservation |
| GET | `/reservations` | Search by status and referenced IDs |
| DELETE | `/reservations/{id}` | Cancel a reservation |
| PATCH | `/reservations/{id}/cancel` | Cancel explicitly |
| PATCH | `/reservations/{id}/complete` | Complete explicitly |

Responses use the existing RFC7807 Problem Details convention. Validation belongs at the REST boundary; lifecycle rules remain inside the aggregate.

## Persistence

The service has its own database/schema and Flyway migration. The table contains reservation identity, referenced IDs, lifecycle status, timestamps, and expiration time. Indexes are required for reservation number, station, charger, user, status, and expiration. No foreign keys reference another microservice.

## Events

The producer publishes immutable version-one records under `events.reservations.v1` (or the equivalent project event package): `ReservationCreated`, `ReservationCancelled`, `ReservationExpired`, and `ReservationCompleted`. Each event carries event and aggregate metadata plus reservation, station, charger, user, vehicle, and expiration data. Publication is asynchronous through `ReservationEventPublisher`; no Kafka consumer is part of this sprint.

## Expiration

A scheduled application use case runs once per minute, finds CREATED or ACTIVE reservations whose expiration time has passed, expires them in a transaction, and publishes `ReservationExpired`. The state transition is idempotent, so rerunning the scheduler cannot emit duplicate transitions for an already terminal reservation.

## Package shape

```text
reservation/
  application/{usecase,service,port}
  domain/{aggregate,model,valueobject,event}
  infrastructure/{persistence,kafka,rest,config}
events/reservations/v1/
```

The application depends on ports only. JPA entities and Kafka adapters remain infrastructure concerns, preserving Hexagonal Architecture and the Reservation bounded context. This gives Sprint 4 a stable event contract for Station Service consumption without coupling the services over REST.
