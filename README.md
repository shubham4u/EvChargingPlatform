# EV Charging Platform

This repository contains a Spring Boot 3 EV charging network platform built as a learning and implementation exercise around modern Java backend architecture.

## What we built

We designed and implemented the project step by step:

1. A documented architecture for an EV charging network platform.
2. Domain-driven boundaries for the main business areas.
3. Immutable Kafka event records with versioning.
4. API design for station and charging workflows.
5. A working Spring Boot 3 microservice using Java 21, Maven, Spring Data JPA, Flyway, PostgreSQL, Redis, Kafka, Docker, Kubernetes, and OpenTelemetry.
6. Local infrastructure with Docker Compose.
7. Deployment support with Kubernetes manifests and GitHub Actions CI.

## Current focus

One Spring Boot application with ten package-by-feature, hexagonal modules. Each module owns its own tables and publishes its own Kafka events:

| Module | What it does | Endpoints |
|---|---|---|
| `identity` | Register drivers | `/users` |
| `vehicle` | Register EVs to a driver | `/vehicles` |
| `station` | Stations and connectors, search by location / connector / power / availability, and live connector status from read models fed by Kafka | `/stations`, `/stations/search`, `/stations/{id}/connectors` |
| `reservation` | Reserve a connector for a time window | `/reservations` |
| `charging` | Start and stop charging sessions | `/charging/sessions` |
| `pricing` | Station tariffs (per kWh, per minute, session fee) and session pricing | `/stations/{id}/tariff` |
| `payments` | Saved cards, a hold at start, capture at stop, refunds, behind a provider port (simulated provider for now) | `/payments` |
| `notifications` | Driver inbox built from events (reservation, charging, payment), plus a delivery port | `/notifications` |
| `audit` | Every domain event as published, with the trace id linking it to the API request | `/audit/events` |
| `reporting` | Per-station usage, revenue, refunds and amounts outstanding | `/reports/stations/{id}` |

A driver signs up, adds their EV, finds a nearby compatible charger that is free right now, reserves it, starts charging, stops, and pays. A payment failure is never silent: a declined card stops the session starting, and a failed final charge ends the session `PAYMENT_FAILED`. Every reference is checked: you can't charge someone else's car, use a connector at the wrong station, take a connector that is busy or reserved by someone else, or charge one car twice at once.

**Login** is handled by Keycloak (OIDC); the API is an OAuth2 resource server. The caller's identity comes from the access token, never the request body. Drivers can only see and change their own things; operators manage stations and can see everything. Station browsing is public.

### Try the full journey

```powershell
docker compose up --build -d
.\scripts\demo-flow.ps1
```

The script signs up three Keycloak accounts (two drivers, one operator) and runs the journey as them. To try it yourself, `.\scripts\new-user.ps1 -Name Alice` creates an account and gives you a token.

See [API smoke test](docs/api-smoke-test.md) for the manual steps and [API design](docs/api-design.md) for every endpoint and error code.

## How we validated it

We tested the application in a few stages:

1. Application startup with Maven and Spring Boot.
2. API smoke checks with PowerShell `Invoke-RestMethod`.
3. Full local stack startup with Docker Compose.

Example checks:

```powershell
mvn spring-boot:run
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/stations
docker compose up --build
```

## What failed and how we fixed it

We ran into a few real issues while proving the app worked:

- `mvn` was not available in one shell session at first. We verified the installed Maven setup and used a shell where Maven was on the PATH.
- Docker Desktop’s Linux engine was not ready initially, so Compose could not connect. After starting Docker Desktop properly, the stack came up.
- The Kafka image tag originally used in Compose could not be resolved. We replaced it with a valid Kafka image and updated the Compose setup.
- `GET /stations/{id}` returned a `500` during the first test. That was traced to Redis caching and serialization behavior, and we fixed it by making the station domain object serializable.

After those fixes, the health check, station creation, and station lookup worked correctly.

## Documentation

Supporting docs are in the `docs/` folder:

- [Architecture](docs/architecture.md)
- [Domain-driven design](docs/domain-driven-design.md)
- [Microservices](docs/microservices.md)
- [Event catalog](docs/event-catalog.md)
- [Database design](docs/database-design.md)
- [API design](docs/api-design.md)
- [API smoke test](docs/api-smoke-test.md)
- [Testing and fixes](docs/testing-and-fixes.md)
- [Station service notes](docs/station-service.md)
- Sprint notes: [2](docs/sprint-2-event-publishing.md) · [3](docs/sprint-3-reservation-service.md) · [4](docs/sprint-4-event-consumption.md) · [5](docs/sprint-5-charging-session-service.md) · [6](docs/sprint-6-users-vehicles-connectors.md) · [7](docs/sprint-7-authentication.md) · [8](docs/sprint-8-station-search-availability.md) · [9](docs/sprint-9-pricing-payments.md) · [10](docs/sprint-10-notifications-audit-reporting.md)

## Local development

The application uses environment-based configuration for sensitive values. For local Docker Compose runs:

1. Copy `.env.example` to `.env`.
2. Set your local `POSTGRES_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`.
3. Start the stack with:

```powershell
docker compose up --build
```

## Security note

Passwords and secrets are not committed to the repository. Local `.env` values and Kubernetes secrets are intentionally kept out of source control.

