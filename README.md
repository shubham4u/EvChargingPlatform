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

The current runtime service is centered on the station feature. It follows a package-by-feature and hexagonal-style structure so the business logic stays separate from web, persistence, and messaging adapters.

Key endpoints include:

- `POST /stations`
- `GET /stations`
- `GET /stations/{id}`
- `GET /actuator/health`

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

## Local development

The application uses environment-based configuration for sensitive values. For local Docker Compose runs:

1. Copy `.env.example` to `.env`.
2. Set your local `POSTGRES_PASSWORD`.
3. Start the stack with:

```powershell
docker compose up --build
```

## Security note

Passwords and secrets are not committed to the repository. Local `.env` values and Kubernetes secrets are intentionally kept out of source control.

