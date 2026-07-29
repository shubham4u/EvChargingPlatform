# Microservices

## Service Boundary Principles

- Each service owns its data.
- Each service exposes a narrow API.
- Cross-service communication prefers events.
- Read models are separate from write models when scale or latency requires it.
- Service boundaries should be derived from the bounded contexts in [Domain-Driven Design](domain-driven-design.md).

## Proposed Services

| Service | Responsibility | Data Ownership | Primary Consumers |
|---|---|---|---|
| Identity Service | Authentication, roles, tenants, service auth | Users, roles, tokens, memberships | Gateway, all services |
| Catalog Service | Station catalog, connectors, tariffs metadata | Stations, connectors, tariff definitions | Search, reservation, session |
| Availability Service | Live connector state and occupancy | Availability snapshots | Search, reservation, admin |
| Reservation Service | Reservation lifecycle | Reservations, holds, expiry state | Driver app, session service |
| Charging Session Service | Session orchestration and meter state | Sessions, meter readings, session commands | Payment, notification, reporting |
| Pricing Service | Tariff calculation | Price quotes, pricing rules | Session, payment |
| Payment Service | Payment authorization, capture, refund | Payment intents, captures, refunds | Session, reporting |
| Notification Service | Emails, SMS, push, alerts | Notification jobs, templates, delivery status | All domain services |
| Charge Point Gateway | Device protocol translation | Device sessions, protocol commands | Session, availability |
| Reporting Service | Projections, audits, support analytics | Read models, reports, audit views | Support, operators |

## Responsibility Notes

- The Catalog Service owns static station data.
- The Availability Service owns rapidly changing live state.
- The Charging Session Service owns the lifecycle of a charging session and coordinates downstream payment and notification workflows.
- The Charge Point Gateway isolates station protocol complexity from business services.
- The Reporting Service should be fed by Kafka events rather than direct database reads.
