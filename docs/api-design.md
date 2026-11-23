# API Design

## Principle

The REST API comes after the domain events and bounded contexts. The API is a delivery layer on top of those domain boundaries, not the source of truth.

## Core Endpoints

The driver journey: sign up in Keycloak → create a profile → add an EV → find a station's connectors → (optionally) reserve one → charge → stop. `scripts/demo-flow.ps1` runs it end to end.

## Authentication

Every endpoint except health and station browsing needs `Authorization: Bearer <access token>`, issued by the Keycloak realm `ev-charging` (`http://localhost:8081` locally). The API checks the signature, expiry, issuer, and that the audience includes `ev-platform-api`.

- **Who you are** comes from the token, never the body: your user id is the token's `sub`. Requests no longer carry `userId` or `ownerUserId`; if one is sent, it is ignored.
- **Roles** are Keycloak realm roles. Every account has `driver`; operators also have `operator`.
- **Ownership:** drivers can only read or change their own profile, vehicles, reservations and sessions. Operators can read and act on everyone's.
- Missing, expired or invalid token → `401 Authentication required` (with `WWW-Authenticate: Bearer`). Authenticated but not allowed → `403 Forbidden`. Both are problem-detail JSON.

Getting a token locally (dev only, the `ev-cli` client allows username + password): `.\scripts\new-user.ps1 -Name Alice` creates an account and returns a token; `.\scripts\get-token.ps1` signs in again. A browser or mobile app would use the authorization code flow with PKCE on the same client.

| Endpoint | Anonymous | Driver | Operator |
|---|---|---|---|
| `GET /actuator/health` | ✅ | ✅ | ✅ |
| `GET /stations`, `GET /stations/search`, `GET /stations/{id}`, `GET /stations/{id}/connectors` | ✅ | ✅ | ✅ |
| `POST /stations`, `POST /stations/{id}/connectors` | 401 | 403 | ✅ |
| `POST /users/me`, `GET /users/me` | 401 | ✅ self | ✅ self |
| `GET /users` | 401 | 403 | ✅ |
| `GET /users/{id}` | 401 | own only | ✅ |
| `POST /vehicles`, `GET /vehicles` | 401 | own | ✅ all (`?ownerUserId=` to filter) |
| `GET /vehicles/{id}` | 401 | own only | ✅ |
| `POST /reservations`, `GET /reservations` | 401 | own | ✅ all |
| `GET /reservations/{id}`, `PATCH …/cancel`, `PATCH …/complete`, `DELETE /reservations/{id}` | 401 | own only | ✅ |
| `POST /charging/sessions`, `GET /charging/sessions` | 401 | own | ✅ all |
| `GET /charging/sessions/{id}`, `PATCH /charging/sessions/{id}/stop` | 401 | own only | ✅ |

## Endpoints

- `POST /users/me` — create your profile from your login (`201`, `409` already registered). Optional body `{ "displayName", "phoneNumber" }`.
- `GET /users/me`; `GET /users` (operator); `GET /users/{id}`
- `POST /vehicles` — register an EV to yourself (`201`, `404` no profile yet, `409` duplicate VIN)
- `GET /vehicles`, `GET /vehicles/{id}`
- `POST /stations` (operator)
- `GET /stations`
- `GET /stations/search` — find stations (see below)
- `GET /stations/{id}`
- `POST /stations/{id}/connectors` (operator) — add a connector (`201`, `404` unknown station)
- `GET /stations/{id}/connectors` — with live `status`
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
POST /users/me            (body optional)
{ "displayName": "Alice", "phoneNumber": "+44 7700 900123" }
→ { "id": "<token sub>", "email": "<token email>", "displayName": "Alice", "phoneNumber": "...", "role": "DRIVER", "createdAt": "..." }

POST /vehicles
{ "vin": "5YJ3E1EA7KF317000", "make": "Tesla", "model": "Model 3" }
→ { "id": "uuid", "ownerUserId": "uuid", "vin": "5YJ3E1EA7KF317000", "make": "Tesla", "model": "Model 3", "createdAt": "..." }

POST /stations/{id}/connectors
{ "connectorType": "CCS2", "maxPowerKw": 150 }
→ { "id": "uuid", "stationId": "uuid", "connectorType": "CCS2", "maxPowerKw": 150, "createdAt": "..." }
```

- The profile's id and email come from the token; `displayName` defaults to the name on the account. Email is stored lower-cased; uniqueness ignores case.
- VIN must be 17 characters, A–Z (except I, O, Q) and 0–9; it is stored upper-cased and must be unique.
- `connectorType` is one of `TYPE2`, `CCS2`, `CHADEMO`, `CCS1`, `NACS`; `maxPowerKw` is 1–1000.

### Finding a station

`GET /stations/search` is public. Every parameter is optional:

| Parameter | Meaning | Default |
|---|---|---|
| `lat`, `lng` | Search point (both or neither) | none: all active stations, by name |
| `radiusKm` | Max distance, up to 200 | 10 |
| `connectorType` | `TYPE2`, `CCS2`, `CHADEMO`, `CCS1`, `NACS` | any |
| `minPowerKw` | At least this power | any |
| `available` | Only connectors free right now | `false` |
| `limit` | 1–100 | 20 |

Connector filters apply per connector: a station matches when one of its connectors meets all of them, and only those connectors are listed. Results are nearest first.

```json
GET /stations/search?lat=51.5308&lng=-0.1238&radiusKm=5&connectorType=CCS2&minPowerKw=100&available=true
→ [ { "id": "uuid", "name": "Kings Cross Hub", "distanceKm": 0.3, "availableConnectors": 1,
      "latitude": 51.53, "longitude": -0.12, "status": "ACTIVE", ...,
      "connectors": [ { "id": "uuid", "connectorType": "CCS2", "maxPowerKw": 150, "status": "AVAILABLE", ... } ] } ]
```

Connector `status` (also on `GET /stations/{id}/connectors`) is `OCCUPIED` while a session runs, otherwise `RESERVED` while an unexpired reservation holds it, otherwise `AVAILABLE`. It comes from the station's own read models, fed by charging and reservation events over Kafka, so it trails a start or stop by a fraction of a second (measured at under 100 ms locally). Starting a session is still checked against the charging context's own data, so a stale status can never let two cars onto one connector.

### Reservation rules

`POST /reservations` (`stationId`, `chargerId`, `vehicleId`, `startTime`; the user is the caller) checks that the caller has a profile (`404 User not found`), the vehicle is theirs (`409 Vehicle not owned by user`), and `chargerId` is a connector at `stationId` (`404 Connector not found`). A reservation holds the connector from `startTime` for 10 minutes; a second open reservation whose window overlaps it on the same connector gets `409 Connector already reserved`. Starting a session against the reservation makes it `ACTIVE`, and stopping the session makes it `COMPLETED`. Only a `CREATED` reservation (the driver never turned up) expires.

### Charging session shapes

Start request (`reservationId` is optional — omit it for walk-up charging; when present, the connector must be `RESERVED` for that user in the Station Service's reservation projection, otherwise `409 Connector not reserved`):

```json
{
  "stationId": "uuid",
  "connectorId": "uuid",
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

