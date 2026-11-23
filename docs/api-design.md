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
| `GET /stations/{id}/tariff` | ✅ | ✅ | ✅ |
| `PUT /stations/{id}/tariff` | 401 | 403 | ✅ |
| `PUT /payments/methods/me`, `GET /payments/methods/me` | 401 | self | self |
| `GET /payments`, `GET /payments/{id}` | 401 | own | ✅ all |
| `POST /payments/{id}/refund` | 401 | 403 | ✅ |
| `GET /notifications`, `PATCH /notifications/{id}/read` | 401 | own | own |
| `GET /audit/events`, `GET /reports/stations/{id}` | 401 | 403 | ✅ |
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
- `PUT /stations/{id}/tariff` (operator) — set prices; `GET /stations/{id}/tariff` — current prices (public)
- `PUT /payments/methods/me` — save your card token; `GET /payments/methods/me`
- `GET /payments`, `GET /payments/{id}`; `POST /payments/{id}/refund` (operator)
- `GET /notifications[?unread=true]`, `PATCH /notifications/{id}/read` — your inbox
- `GET /audit/events?key=&type=&traceId=&limit=` (operator) — the event history
- `GET /reports/stations/{id}?from=&to=` (operator) — usage and money for a period (default: last 30 days)

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

### Prices and payment

An operator sets a station's tariff: `PUT /stations/{id}/tariff` with `{ "currency": "GBP", "pricePerKwh": 0.45, "pricePerMinute": 0.05, "sessionFee": 0.50 }`. Each change is a new version. A session is billed at the tariff in force when it **started**.

**Cost** = session fee + kWh × price per kWh + minutes plugged in × price per minute. The parts are added at full precision, and the total is rounded once, half up, to 2 decimals.

Drivers save a card once with `PUT /payments/methods/me` and `{ "token": "tok_visa" }`. The token comes from the payment provider; no card numbers are ever stored. Then:

| Step | What happens | If it fails |
|---|---|---|
| Start | A £50 hold (`app.payments.hold-amount`) is placed on the card | No card → `402 Payment method required`. Declined → `402 Payment declined` (with `failureCode`); no session is created, but the decline is recorded. No tariff → `404 Tariff not found`. |
| Stop | The session is priced and that amount is captured | The session ends `PAYMENT_FAILED` instead of `STOPPED`, with `totalCost` still set. The stop itself succeeds and the connector is freed. A cost above the hold also fails, with `amount_exceeds_authorization`. |
| Refund (operator) | `POST /payments/{id}/refund` refunds a `CAPTURED` payment in full | Anything else → `409` |

The session response now includes `tariffId`, `totalCost` and `currency`. Payment events (`PaymentAuthorized`, `PaymentCaptured`, `PaymentFailed`, `PaymentRefunded`) go to `payment.events.v1`, keyed by session id, with amounts in minor units.

### Notifications, audit and reports

**Notifications.** Drivers get an inbox entry, also passed to a delivery port (a logging stand-in for email or push), for: reservation confirmed, cancelled or expired; charging started or finished; payment received; card declined ("charging didn't start"); payment failed ("please update your card"); and refund issued. They are built only from Kafka events, each at most once.

**Audit.** Every event on every `*.events.v1` topic is stored exactly as published, with its type, key and the W3C **trace id** it was published under. `GET /audit/events?key=<sessionId>` lists everything that happened to a session. For example:

```
PaymentAuthorized → ChargingSessionRequested → ChargingSessionStarted   (trace A: POST /charging/sessions)
PaymentCaptured → ChargingSessionStopped                                (trace B: PATCH …/stop)
PaymentRefunded                                                         (trace C: POST /payments/{id}/refund)
```

Open a trace id in Jaeger (http://localhost:16686/trace/&lt;traceId&gt;) to see the HTTP request and every event it published.

**Reports.** `GET /reports/stations/{id}` returns `sessions`, `activeSessions`, `paidSessions`, `paymentFailures`, `energyKwh`, `billed`, `revenue` (captured minus refunds), `refunded`, `outstanding` (the cost of `PAYMENT_FAILED` sessions) and `currency`.

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
  "status": "REQUESTED | ACTIVE | STOPPED | PAYMENT_FAILED",
  "startedAt": "2026-11-22T15:55:42.355470Z",
  "stoppedAt": "2026-11-22T15:55:42.477032Z | null",
  "totalEnergyKwh": 18.25,
  "tariffId": "uuid",
  "totalCost": 8.71,
  "currency": "GBP",
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

