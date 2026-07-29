# EV Charging Network Platform Requirements

## Functional Requirements

### FR-1 Station discovery

Users must be able to search stations by location, connector type, power level, availability, and operator.

### FR-2 Station and connector catalog

The platform must store stations, connectors, capabilities, access rules, and tariffs.

### FR-3 Live availability

The platform must expose current connector availability and operational status.

### FR-4 Reservation

The platform should support optional reservations for compatible stations and connectors.

### FR-5 Charging session lifecycle

The platform must support session initiation, metering updates, stop requests, completion, and settlement.

### FR-6 Pricing

The platform must calculate charging costs using tariffs, time windows, energy usage, and possible fees.

### FR-7 Payments

The platform must authorize, capture, refund, and reconcile payments through an external payment provider.

### FR-8 Notifications

The platform must send notifications for reservation changes, session events, failed payments, and operational incidents.

### FR-9 Administration

Operators must be able to onboard stations, manage tariffs, view health, and investigate failures.

### FR-10 Audit and reporting

The platform must retain audit history for compliance, dispute handling, and operational reporting.

## Non-Functional Requirements

### NFR-1 Technology stack

- Java 21
- Spring Boot 3
- Kafka
- PostgreSQL
- Redis
- Kubernetes

### NFR-2 Scalability

The system must scale horizontally for read-heavy discovery traffic and event-heavy session workflows.

### NFR-3 Reliability

Charging commands and financial events must be processed with at-least-once delivery and idempotent handlers.

### NFR-4 Security

The platform must use strong authentication, authorization, encrypted transport, and audit logging.

### NFR-5 Performance

Search and availability APIs should be optimized for low latency through caching and read models.

### NFR-6 Operability

Services must expose health checks, metrics, logs, and traces suitable for Kubernetes operations.

## Acceptance Criteria

- A user can find a compatible station using location and connector filters.
- A user can start and stop a charging session through the platform.
- A payment failure does not silently complete a charging session.
- A station status change propagates to the read side within an acceptable delay.
- Operators can trace every major business action from API request to persisted event.

