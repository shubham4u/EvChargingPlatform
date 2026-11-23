# Sprint 8 — Station Search and Live Availability

> Status: **done**. Written after the work. Builds on Sprint 7 (merged as PR #11).

## Goal

Meet the first acceptance criterion in `requirements.md`, "A user can find a compatible station using location and connector filters" (FR-1), and expose live connector availability (FR-3). Doing that properly meant also fixing the reservation ↔ session lifecycle carried over from Sprints 6–7. Without it, a connector would keep showing as reserved after the driver had left.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where search lives | Station module, `GET /stations/search`, public | The Catalog owns stations and connectors; browsing needs no login (Sprint 7) |
| How Station learns a connector is in use | A new **occupancy read model**, built from `charging.events.v1` (Started → row, Stopped → no row) | Station can't call Charging in-process: Charging already depends on Station, so that would make the two depend on each other. It's also the read-model pattern Sprint 4 set up and the one NFR-5 and acceptance criterion 4 ask for. |
| Status precedence | `OCCUPIED` > `RESERVED` (unexpired hold) > `AVAILABLE` | A driver charging against their reservation is occupying the connector |
| Correctness vs freshness | Search can be up to ~100 ms stale; **starting a session is still checked against Charging's own data** | A stale status can't put two cars on one connector; at worst a driver sees "available" a moment too long |
| Geo query | Bounding-box SQL prefilter + exact haversine in memory; index `stations(status, latitude, longitude)` | No new dependency; fine for thousands of stations. PostGIS is the upgrade path. |
| Connector filters | Per connector: a station matches when one connector meets all filters, and only matching connectors are listed | "A CCS2 ≥100 kW that's free" must be one connector, not three different ones |
| Reservation lifecycle | Session start **activates** the reservation (same transaction); stop **completes** it if still active; only `CREATED` reservations expire | Completion publishes `ReservationCompleted`, which frees the hold on the read side. An `ACTIVE` reservation used to be expired mid-charge by the scheduler. |

**Why `completeIfActive` instead of calling `complete` and catching the exception:** an exception thrown through another `@Transactional` bean marks the shared transaction rollback-only, even if the caller catches it, so the session stop itself would have failed. The reservation may legitimately be cancelled or expired by the time a session ends, and that must not block the stop.

**Out-of-order events:** charging events are keyed by session id, so one session's events stay in order, but two sessions on the same connector can sit on different partitions. The projection never lets an older session's start overwrite a newer one, and a stop only clears the connector if it still shows that session. Redelivery is harmless.

## What was built

- **Endpoints:**
  - `GET /stations/search?lat&lng&radiusKm&connectorType&minPowerKw&available&limit`, returning distance, connectors and `availableConnectors`
  - `GET /stations/{id}/connectors` and `POST …/connectors` now include `status`
  - malformed query values (e.g. `connectorType=SCHUKO`) return a 400 problem response
- **Station module:**
  - `StationSearchService` (search and availability)
  - `OccupancyProjectionService`
  - `KafkaChargingEventConsumer`: one class-level listener with a default handler for `Requested`
  - `GeoDistance`
  - `ConnectorOccupancy` with its adapter and entity
- **Migration `V9`:** `connector_occupancy`, plus indexes for the geo prefilter and per-connector hold lookups.
- **Reservation:** `ActivateReservationUseCase`, `CompleteReservationUseCase.completeIfActive`, and expiry limited to `CREATED`.
- **Charging:** activates on start and completes on stop.
- **Demo:** 43 checks, up from 31. Each run uses a random station location, so stations left by earlier runs don't crowd the results.

## Tests

**199 tests, 0 failures, 0 errors — BUILD SUCCESS** (175 → 199).

| Test class | Tests | Covers |
|---|---|---|
| `GeoDistanceTest` | 4 | London–Paris ≈ 344 km, symmetry, bounding box contains the circle, antimeridian/pole widening |
| `StationSearchServiceTest` | 9 | radius filter and nearest-first, by-name without location, limit, per-connector filters, stations without connectors, occupied > reserved, expired hold ignored, `available=true`, query validation |
| `OccupancyProjectionServiceTest` | 6 | start/stop, redelivery, stale start ignored, another session's stop ignored |
| `StationSearchControllerTest` | 3 | public, parameter mapping, defaults, 400s |
| `StationSearchPersistenceTest` | 2 | bounding-box query on H2 with the real V1–V9 schema, batch connector load, occupancy round-trip |
| `ChargingSessionApplicationServiceTest` | 23 (+2) | reservation activated on start, completed on stop, untouched for walk-ups |
| `ReservationApplicationServiceTest` | 20 (+4) | activate, refuse activating a terminal one, `completeIfActive` both ways, expiry asks for `CREATED` only |

**Test bug fixed:** I had guessed King's Cross → Canary Wharf at "about 8 km". Haversine said 7.49 km, and a hand calculation agrees: 2.83 km north–south and 6.94 km east–west give √(2.83² + 6.94²) = 7.49. The expectation was corrected, not the code.

## End-to-end verification

On the Docker Compose stack, 2026-11-23:
- V9 was applied with 0 `ERROR` lines at startup.
- The new consumer replayed **33** historical charging events from the topic with **0 listener failures**. That also confirms the default handler absorbs `Requested` events.
- `scripts/demo-flow.ps1` passed **all 43 checks**:

```
== 4b. A driver searches for a fast CCS2 charger nearby (no login needed)
  found   Kings Cross Hub 3DFC6AC8  0.3 km away  1 available
  [PASS] search finds the station about 0.3 km away
  [PASS] only the matching CCS2 150 kW connector is listed, AVAILABLE
  [PASS] asking for 200 kW excludes it
  [PASS] searching from Paris excludes it
== 5.  [PASS] CCS2 connector now shows RESERVED
== 7.  [PASS] CCS2 connector now shows OCCUPIED
       [PASS] her reservation is now ACTIVE
== 8.  [PASS] search with available=true no longer offers this station
== 9.  [PASS] her reservation is COMPLETED
       [PASS] CCS2 connector is AVAILABLE again (not left RESERVED)
== 10. [PASS] both connectors AVAILABLE, and search offers the station again
All 43 checks passed.
```

**Propagation delay** (acceptance criterion 4), measured by polling `GET /stations/{id}/connectors` every 20 ms after each API call, over 5 start/stop cycles:

| Run | Start → `OCCUPIED` | Stop → `AVAILABLE` |
|---|---|---|
| 1 | 29 ms | 85 ms |
| 2 | 45 ms | 64 ms |
| 3 | 13 ms | 63 ms |
| 4 | 13 ms | 67 ms |
| 5 | 18 ms | 16 ms |

The worst case was **85 ms** on the local stack. Stop is slower because it also completes the reservation.

The new search steps in `docs/api-smoke-test.md` were run as written.

**Environment note:** the first stack start hit a Docker Desktop engine error (`500` from the engine API while starting a container). Nothing in the app caused it. The retry started cleanly.

## Requirements status after this sprint

| | Before | After |
|---|---|---|
| FR-1 Station discovery | ❌ | ✅ location, connector type, power, availability. Operator is still missing: stations have no operator field. |
| FR-3 Live availability | 🟡 | ✅ |
| Acceptance 1: find a compatible station | ❌ | ✅ |
| Acceptance 4: status change reaches the read side quickly | ❌ | ✅ under 100 ms locally |
| Open item: reservation ↔ session lifecycle | ❌ | ✅ |

## Open items

1. **Search by operator** (the rest of FR-1). It needs an operator or company on stations.
2. **Read models when Kafka is off.** With `app.messaging.enabled=false` (plain `mvn spring-boot:run`), occupancy and holds stay empty, so search shows everything as available. Charging's own checks still apply.
3. **Read-model drift after a database reset.** If Postgres is wiped but Kafka keeps its history, replay can recreate occupancy for connectors that no longer exist. That's harmless, but a rebuild-projection admin task would be tidy.
4. **A long charge past its reservation window.** Overlap checks use the reservation's 10-minute window, so someone can reserve a slot while the previous car is still charging. Charging will refuse the start ("connector in use"), but the reservation shouldn't have been offered.
5. **Race conditions** (carried over): needs partial unique indexes in Postgres.
6. **Pricing and payments** (FR-6 and FR-7) are next. They're needed for acceptance criterion 3.
7. **Events published before commit** (carried over): needs an outbox.
