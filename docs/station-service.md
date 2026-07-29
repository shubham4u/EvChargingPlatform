# Station Service

This Spring Boot 3 microservice manages charging-station registration and lookup. It uses Java 21, Maven, Spring Cloud Config, Kafka, Spring Data JPA, Flyway, PostgreSQL, Redis, Actuator, OpenTelemetry, Docker, Kubernetes, and a package-by-feature hexagonal structure.

## Structure

```text
station/
  domain/                  Business model and domain exceptions
  application/             Use cases and ports
  adapter/in/web/          HTTP controller and request/response models
  adapter/out/persistence/ JPA implementation of the repository port
shared/web/                Cross-feature HTTP exception handling
```

The application layer depends on the `StationRepository` output port. The persistence adapter is its JPA/PostgreSQL implementation; the domain has no Spring or JPA dependency.

## Run locally

Start PostgreSQL (using the configured defaults):

```powershell
$env:POSTGRES_PASSWORD = "choose-a-local-password"
docker run --name ev-postgres -e POSTGRES_DB=ev_charging -e POSTGRES_USER=ev_charging -e POSTGRES_PASSWORD=$env:POSTGRES_PASSWORD -p 5432:5432 -d postgres:16
```

Then start the service:

```powershell
mvn spring-boot:run
```

Flyway creates the `stations` table automatically. Java 21 and Maven 3.9+ are required.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/stations` | Register a station |
| `GET` | `/stations` | List stations |
| `GET` | `/stations/{id}` | Get one station |
| `GET` | `/actuator/health` | Liveness/readiness health status |

Example request:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/stations -ContentType application/json -Body '{"externalReference":"LON-001","name":"London Central","countryCode":"GB","latitude":51.5074,"longitude":-0.1278}'
```

## Local platform

Start the service with PostgreSQL, Redis, Kafka, and Jaeger:

```powershell
docker compose up --build
```

Before using Docker Compose, copy `.env.example` to `.env` and set `POSTGRES_PASSWORD`. The `.env` file is intentionally ignored by Git.

Kafka publishing is disabled by default for a simple local application run. Docker Compose enables it through `KAFKA_ENABLED=true`. Station creation then publishes a versioned `StationRegisteredEvent` to `station.events.v1`.

Redis stores `GET /stations/{id}` results for ten minutes. OpenTelemetry traces are exported through OTLP; the Compose stack displays them in Jaeger at `http://localhost:16686`.

## Build container

```powershell
mvn package
docker build -t station-service .
docker run --rm -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/ev_charging -e DB_PASSWORD=$env:POSTGRES_PASSWORD station-service
```

## Deployment and CI

Create the database Secret outside source control, then apply the deployment baseline:

```powershell
kubectl create secret generic station-service-secrets --from-literal=DB_USERNAME=ev_charging --from-literal=DB_PASSWORD='your-production-password'
kubectl apply -f k8s/station-service.yaml
```

Replace the example image name before production use. The manifest uses Actuator liveness and readiness endpoints.

GitHub Actions runs `mvn verify` on pull requests and `main`, then validates the Docker build. See `.github/workflows/ci.yml`.
