# API Design

## Principle

The REST API comes after the domain events and bounded contexts. The API is a delivery layer on top of those domain boundaries, not the source of truth.

## Core Endpoints

The driver journey: register → add an EV → find a station's connectors → (optionally) reserve one → charge → stop. `scripts/demo-flow.ps1` runs it end to end.

- `POST /users` — register a driver (`201`, `409` duplicate email)
- `GET /users`, `GET /users/{id}`
- `POST /vehicles` — register an EV to a user (`201`, `404` unknown owner, `409` duplicate VIN)
- `GET /vehicles`, `GET /vehicles?ownerUserId={id}`, `GET /vehicles/{id}`
- `POST /stations`
- `GET /stations`
- `GET /stations/{id}`
- `POST /stations/{id}/connectors` — add a connector (`201`, `404` unknown station)
- `GET /stations/{id}/connectors`
- `POST /reservations`
- `GET /reservations`, `GET /reservations/{id}`
- `PATCH /reservations/{id}/cancel`, `PATCH /reservations/{id}/complete`
- `POST /charging/sessions` — start a charging session (`201 Created`)
- `PATCH /charging/sessions/{id}/stop` — stop an active session (`200 OK`)
- `GET /charging/sessions/{id}` — read one session (`200 OK`, `404` if unknown)
- `GET /charging/sessions` — list sessions (`200 OK`)

Charging follows the Reservation convention (`POST` the collection to create, `PATCH /{id}/<transition>` for state changes) rather than the verb-style `POST /charging/start` / `POST /charging/stop` originally planned here — see Decision 2 in `sprint-5-charging-session-service.md`.

### User, vehicle and connector shapes

```json
POST /users
{ "email": "alice@example.com", "displayName": "Alice", "phoneNumber": "+44 7700 900123" }
→ { "id": "uuid", "email": "alice@example.com", "displayName": "Alice", "phoneNumber": "...", "role": "DRIVER", "createdAt": "..." }

POST /vehicles
{ "ownerUserId": "uuid", "vin": "5YJ3E1EA7KF317000", "make": "Tesla", "model": "Model 3" }
→ { "id": "uuid", "ownerUserId": "uuid", "vin": "5YJ3E1EA7KF317000", "make": "Tesla", "model": "Model 3", "createdAt": "..." }

POST /stations/{id}/connectors
{ "connectorType": "CCS2", "maxPowerKw": 150 }
→ { "id": "uuid", "stationId": "uuid", "connectorType": "CCS2", "maxPowerKw": 150, "createdAt": "..." }
```

- Email is stored lower-cased; uniqueness ignores case. `phoneNumber` is optional.
- VIN must be 17 characters, A–Z (except I, O, Q) and 0–9; it is stored upper-cased and must be unique.
- `connectorType` is one of `TYPE2`, `CCS2`, `CHADEMO`, `CCS1`, `NACS`; `maxPowerKw` is 1–1000.
- There is no authentication yet: `userId` / `ownerUserId` are passed in the body and checked against registered users.

### Reservation rules

`POST /reservations` (`stationId`, `chargerId`, `userId`, `vehicleId`, `startTime`) now checks that the user exists (`404 User not found`), the vehicle is theirs (`409 Vehicle not owned by user`), and `chargerId` is a connector at `stationId` (`404 Connector not found`). A reservation holds the connector from `startTime` for 10 minutes; a second open reservation whose window overlaps it on the same connector gets `409 Connector already reserved`.

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

Before a session starts, the same user / vehicle-ownership / connector-at-station checks as reservations apply, plus:

| Situation | Response |
|---|---|
| Connector already has an `ACTIVE` session | `409 Connector in use` |
| Walk-up start (no `reservationId`) on a connector held by an unexpired reservation | `409 Connector in use` |
| Vehicle already has an `ACTIVE` session elsewhere | `409 Vehicle already charging` |

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

