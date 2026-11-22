# Sprint 6 — Users, Vehicles and Connectors

> Status: **done**. Written after the work, like sprints 2–4. Builds on `fix/sprint-5-hardening` (persistence `@Version` fix, Kafka topic config, `@DataJpaTest` setup).

## Goal

Make the driver journey real end to end: a user registers, adds their EV, picks a connector at a station, optionally reserves it, charges, and stops.

Before this sprint, charging and reservations accepted any `userId`, `vehicleId` and `connectorId` UUID without checking it, and stations had no connectors at all.

## Scope decisions (agreed before starting)

| Question | Decision |
|---|---|
| What's in the sprint | Users, vehicles, and station connectors, with charging and reservations validating every reference. Search, pricing and payment are left for later sprints. |
| Authentication | **None yet.** Callers pass `userId` in the body, as before; it is now checked against registered users. Real OIDC is its own sprint (`architecture.md` targets OAuth 2.1 / OIDC). |

## Design decisions

**1. Module boundaries follow `microservices.md`.** Users live in a new `identity/` module (the Identity Service). Connectors live in `station/`, because the Catalog Service owns stations and connectors. Vehicles get their own `vehicle/` module; `microservices.md` lists no vehicle service, but the `events.vehicles.v1` contract already existed, so they were always meant to be separate.

**2. Cross-context checks go through the other module's inbound query use case.** Examples: `GetUserUseCase.findById`, `GetVehicleUseCase.findOwnedBy`, `GetConnectorsUseCase.findAtStation`. This is the same single-deployable shortcut as Sprint 5 Decision 1, with one difference. Sprint 5 injected the station's *outbound* repository port; these calls go through *inbound* use cases, so no module reaches into another's persistence. If a module is split out later, each call becomes either an HTTP call or a local projection.

**3. "Connector in use" is derived, not stored.** Connectors have no status column. Charging answers "is this connector busy?" from its own `REQUESTED`/`ACTIVE` sessions, and "is it held?" from the reservation projection. That avoids a new cross-context write. If Charging set a status on Station's connector, it would need a Kafka consumer in Station or an in-process write into another context, and the Sprint 5 plan specifically warned against adding consumers under time pressure. Live occupancy belongs to the future Availability Service.

**4. No cross-context foreign keys.** `vehicles.owner_user_id` has no FK to `users`, matching how reservations and charging store other contexts' ids. `connectors.station_id` does have an FK to `stations`, because both tables belong to the station context.

**5. Raw UUIDs** for all foreign references, as in Sprint 5 Decision 3.

## What was built

| Module | Endpoints | Events |
|---|---|---|
| `identity` | `POST /users`, `GET /users`, `GET /users/{id}` | `UserRegisteredEvent` → `user.events.v1` |
| `vehicle` | `POST /vehicles`, `GET /vehicles[?ownerUserId=]`, `GET /vehicles/{id}` | `VehicleRegisteredEvent` → `vehicle.events.v1` |
| `station` | `POST /stations/{id}/connectors`, `GET /stations/{id}/connectors` | none for connectors (see open items) |

Each module has the same hexagonal shape as `reservation/` and `charging/`: domain record and exceptions, `port/in` use cases, `port/out` repository and publisher, a web adapter, a JPA adapter, and a Kafka publisher gated by `app.messaging.enabled`.

Migrations:
- `V6__create_users.sql`: `email` is unique.
- `V7__create_vehicles.sql`: `vin` is unique, indexed by owner.
- `V8__create_connectors.sql`: FK to `stations`, `CHECK (max_power_kw > 0)`. Also adds two indexes that back the new queries: `charging_sessions(vehicle_id)` and `reservations(charger_id, status, start_time, expiration_time)`.

## Rules now enforced

| Where | Rule | Response |
|---|---|---|
| Users | Email unique, case-insensitive (stored lower-cased) | `409 User already exists` |
| Vehicles | Owner must exist | `404 User not found` |
| Vehicles | VIN is 17 chars, A–Z except I/O/Q, 0–9; unique | `400` / `409 Vehicle already registered` |
| Connectors | Station must exist; type is `TYPE2`/`CCS2`/`CHADEMO`/`CCS1`/`NACS`; 1–1000 kW | `404` / `400` |
| Reservations + charging | User exists | `404 User not found` |
| Reservations + charging | Vehicle belongs to the user | `409 Vehicle not owned by user` |
| Reservations + charging | Connector is at the given station | `404 Connector not found` |
| Reservations | No overlapping open (`CREATED`/`ACTIVE`) reservation on the connector | `409 Connector already reserved` |
| Charging | Connector has no open session | `409 Connector in use` |
| Charging | Walk-up start refused while an unexpired reservation holds the connector | `409 Connector in use` |
| Charging | Vehicle has no open session elsewhere | `409 Vehicle already charging` |

`ConnectorAlreadyReservedException` had existed since Sprint 3 but nothing threw it. The reservation overlap check is its first real use.

## Tests

**145 tests, 0 failures, 0 errors — BUILD SUCCESS.** That's 85 before this sprint and 60 new or extended:

| Test class | Tests | Notes |
|---|---|---|
| `UserTest`, `VehicleTest` | 2, 7 | Email normalisation, VIN rules (parameterised), ownership |
| `UserApplicationServiceTest`, `VehicleApplicationServiceTest`, `ConnectorApplicationServiceTest` | 4, 7, 4 | |
| `ChargingSessionApplicationServiceTest` | 21 (+7) | Unknown user, vehicle not owned, wrong station, connector busy, vehicle busy, walk-up on a held connector, expired hold ignored |
| `ReservationApplicationServiceTest` | 16 (+4) | Unknown user, vehicle not owned, wrong station, overlap |
| `UserControllerTest`, `VehicleControllerTest`, `StationConnectorControllerTest`, `StationControllerTest` | 5, 6, 5, 4 | |
| `UserJpaAdapterTest`, `VehicleJpaAdapterTest`, `ConnectorPersistenceAdapterTest` | 1, 1, 1 | H2 + real Flyway V1–V8, `ddl-auto: validate` |
| `ChargingSessionJpaAdapterTest`, `ReservationJpaAdapterTest` | 3 (+1), 2 (+1) | Open-session queries; overlap boundaries (touching windows don't overlap; cancelled doesn't block) |

New tests use a generic `support/TestObjectProvider` instead of copying the per-test `ObjectProvider` inner class.

## End-to-end verification

`scripts/demo-flow.ps1` was run against `docker compose up --build -d` on 2026-11-22, with Flyway V1–V8 applied and 0 `ERROR` lines at startup. All **21 checks passed**, each refusal returning the expected problem title:

```
== 1. Register a driver            [PASS] same email again -> 409 User already exists
== 2. Register her EV              [PASS] vehicle for unknown user -> 404 User not found
== 3. Station with connectors      [PASS] station lists 2 connectors
== 4. Reserve the CCS2 connector   [PASS] overlapping reservation on same connector -> 409 Connector already reserved
== 5. Someone else                 [PASS] Bob charging Alice's car -> 409 Vehicle not owned by user
                                   [PASS] Bob walking up to Alice's reserved connector -> 409 Connector in use
                                   [PASS] connector from a different station -> 404 Connector not found
== 6. Charge against reservation   [PASS] session ACTIVE, linked to reservation
                                   [PASS] same car starting a second session -> 409 Vehicle already charging
== 7. Bob on the free connector    [PASS] a third car on the busy TYPE2 connector -> 409 Connector in use
== 8. Unplug                       [PASS] STOPPED, 42.75 kWh; stopping twice -> 409 Invalid state transition
All checks passed.   (exit code 0)
```

Kafka after two runs: `user.events.v1` 4, `vehicle.events.v1` 4, `charging.events.v1` 12 (2 sessions × 3 events × 2 runs), `reservation.events.v1` 2.

The manual steps in `docs/api-smoke-test.md` were also run exactly as written, with unique email, VIN and station reference substituted.

**Pre-existing bug found and fixed:** `POST /stations` without `countryCode`, `latitude` or `longitude` reached the `NOT NULL` columns and returned a raw **500**, confirmed against the stack. `CreateStationRequest` now marks them `@NotNull`, so the response is `400 Validation failed`, covered by `StationControllerTest` (4 tests).

**Script bug found and fixed while verifying:** Windows PowerShell 5.1 returns a JSON array from `Invoke-RestMethod` as a single pipeline object, so `@(...).Count` was 1 for a 2-connector station. It also leaves `$_.ErrorDetails` empty for error responses. The script now counts via `Measure-Object` and reads the problem body from the response stream. The API itself was correct; `curl.exe` confirmed both.

## Docs updated

- `api-design.md`: all endpoints, shapes, and the rules table.
- `openapi.yaml`: regenerated from the real DTOs. It also fixes pre-existing drift: `servers: /api` → `http://localhost:8080`, `externalRef` → `externalReference`, and the reservation `connectorId`/`expiresAt` → `chargerId`/`startTime`. Parsed with SnakeYAML; no unresolved `$ref`s.
- `api-smoke-test.md`: rewritten. It described `POST /charging/start` and in-memory storage.
- `README.md`: module table and how to run the demo.

## Open items (candidates for next sprints)

1. **Authentication.** Anyone can act as any `userId`. This is the biggest gap before real users. Plan: Keycloak in Compose, Spring Security resource server, `userId` taken from the token.
2. **Reservation ↔ session lifecycle.** Starting a session against a reservation doesn't move the reservation to `ACTIVE`/`COMPLETED`; it expires on its own 10 minutes after `startTime`. Charging events should drive it.
3. **Race conditions.** "Connector in use", "vehicle already charging" and the reservation overlap are check-then-insert. Two simultaneous requests can both pass. The fix is a partial unique index (`… WHERE status IN ('REQUESTED','ACTIVE')`) in Postgres. H2 can't run partial indexes, so it needs either a Postgres-only migration or Testcontainers for the persistence tests.
4. **Station search and availability**: by location, connector type, power (requirement in `requirements.md`).
5. **Pricing and payment**, per the delivery sequence in `architecture.md`.
6. **Connector events.** `ConnectorUpdatedEvent` exists, but adding a connector publishes nothing yet.
7. **Events published before commit**: carried over from Sprint 5; it needs an outbox.
