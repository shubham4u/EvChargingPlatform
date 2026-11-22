# API Design

## Principle

The REST API comes after the domain events and bounded contexts. The API is a delivery layer on top of those domain boundaries, not the source of truth.

## Core Endpoints

- `POST /stations`
- `GET /stations`
- `GET /stations/{id}`
- `POST /reservations`
- `GET /reservations/{id}`
- `POST /charging/sessions` — start a charging session (`201 Created`)
- `PATCH /charging/sessions/{id}/stop` — stop an active session (`200 OK`)
- `GET /charging/sessions/{id}` — read one session (`200 OK`, `404` if unknown)
- `GET /charging/sessions` — list sessions (`200 OK`)

Charging follows the Reservation convention (`POST` the collection to create, `PATCH /{id}/<transition>` for state changes) rather than the verb-style `POST /charging/start` / `POST /charging/stop` originally planned here — see Decision 2 in `sprint-5-charging-session-service.md`.

### Charging session shapes

Start request (`reservationId` is optional — omit it for walk-up charging; when present, the connector must be `RESERVED` for that user in the Station Service's reservation projection, otherwise `409 Connector not reserved`):

```json
{
  "stationId": "uuid",
  "connectorId": "uuid",
  "userId": "uuid",
  "vehicleId": "uuid",
  "reservationId": "uuid | null"
}
```

Stop request (stopping a session that is not `ACTIVE` returns `409 Invalid state transition`):

```json
{ "totalEnergyKwh": 18.25 }
```

Session response (all endpoints):

```json
{
  "id": "uuid",
  "chargingSessionNumber": "ChargingC1C9BED0",
  "stationId": "uuid",
  "connectorId": "uuid",
  "userId": "uuid",
  "vehicleId": "uuid",
  "reservationId": "uuid | null",
  "status": "REQUESTED | ACTIVE | STOPPED",
  "startedAt": "2026-11-22T15:55:42.355470Z",
  "stoppedAt": "2026-11-22T15:55:42.477032Z | null",
  "totalEnergyKwh": 18.25,
  "createdAt": "2026-11-22T15:55:42.267146Z",
  "updatedAt": "2026-11-22T15:55:42.477032Z"
}
```

Missing required fields return `400 Validation failed`.

## Contract Rules

- Use UUID identifiers.
- Use `Instant`-backed timestamps in JSON as ISO-8601 strings.
- Validate requests at the edge.
- Return consistent problem responses for errors.
- Keep POST endpoints command-oriented and GET endpoints read-oriented.

## DTO Rules

- Requests and responses are separate records.
- Do not expose internal JPA entities.
- Keep request payloads minimal and explicit.

## OpenAPI

The service should publish an OpenAPI document that mirrors the REST resources and the validation rules on the DTOs.

