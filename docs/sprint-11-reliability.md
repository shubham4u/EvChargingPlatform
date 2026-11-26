# Sprint 11 — Reliability

> Status: **done**. Written after the work. Stacked on Sprint 10 (`feature/sprint-10-notifications-audit-reporting`), so merge Sprints 9, 10 and 11 in order.

## Goal

NFR-3 ("charging commands and financial events must be processed with at-least-once delivery and idempotent handlers"), plus the reliability gaps carried since Sprint 5:
1. **Events published before commit.** A rolled-back change could still announce itself, and a request blocked when Kafka was down.
2. **Check-then-insert races.** Two simultaneous requests could both take one connector.
3. **No way to collect from `PAYMENT_FAILED` sessions.**

## Decisions

| Question | Decision | Why |
|---|---|---|
| Event delivery | **Transactional outbox.** `EventOutbox.send(topic, key, event)` writes to `outbox_events` in the caller's transaction (`MANDATORY`). `OutboxRelay` publishes committed rows to Kafka and marks them sent. | An event exists exactly when its change does; the API doesn't need Kafka to be up |
| Publisher changes | Same method shape as `KafkaTemplate.send`, so the six publishers only swapped the type they depend on | No business code changed |
| Order | Relay sends in insertion order (identity id) and stops the batch at the first failure | A session's Started is never sent before its Requested |
| Several app instances | Rows claimed with `PESSIMISTIC_WRITE` + `SKIP LOCKED` | k8s runs 2 replicas; they share the work without double-sending |
| Duplicates | At-least-once: a crash between the broker's acknowledgement and the commit resends. Every consumer already dedupes on `eventId` (projections, notifications, audit). | NFR-3 |
| Trace link | The outbox stores trace and span ids; the relay sends inside a span parented on them | The audit-to-Jaeger link from Sprint 10 keeps working |
| Declined hold's event | Moved into the decline recorder's own (`REQUIRES_NEW`) transaction | With an outbox, publishing it from the rolled-back start would have deleted the event |
| Double-booking guard | Postgres partial unique indexes (one open session per connector and per vehicle) and an **exclusion constraint** (`btree_gist`) against overlapping open reservations. Violations are translated to the usual 409s. | The database decides a true race; the application check stays for clear messages |
| Portability | `spring.flyway.locations: db/migration, db/vendor/{vendor}`. H2's V14 builds the session rule from generated columns + unique indexes; it has no exclusion constraint. | Tests run the real constraint for sessions |
| Unpaid sessions | `POST /charging/sessions/{id}/pay`: a fresh charge on the card now on file. Success → payment `CAPTURED`, session `STOPPED`, "payment received" notification. Failure → 402 and nothing changes. | Closes the loop opened by acceptance criterion 3 |

## What was built

- **`shared/outbox`:** `EventOutbox`, `OutboxRelay` (poll 200 ms, purge published rows after 7 days), `OutboxSender` / `KafkaOutboxSender`, the entity and repository.
- **Publishers:** all six publishers write to the outbox; the station publisher's send-callback logging moved to the relay.
- **Payments:**
  - `PaymentIntent.recovered` / `stillFailing`
  - `ChargingPaymentUseCase.collectOutstanding`
  - the recorder publishes the decline event in its own transaction
- **Charging:**
  - `ChargingSession.paymentRecovered`
  - `payOutstanding`
  - `POST /charging/sessions/{id}/pay`
  - adapters flush on save and map constraint names to `ConnectorInUse` / `VehicleAlreadyCharging` / `ConnectorAlreadyReserved`
- **Migrations:**
  - `V13__create_outbox` (portable)
  - `db/vendor/postgresql/V14__guard_double_booking`
  - `db/vendor/h2/V14__guard_double_booking`
- **Demo:** steps 14 (Dave pays later) and 15 (a real concurrent race), 82 checks in total.

## Tests

**281 tests, 0 failures, 0 errors — BUILD SUCCESS** (263 → 281).

- `OutboxTest` (4):
  - writes topic, key, type, payload and trace; works without tracing
  - relays in order back to equal records
  - a failure stops the batch and records the attempt
- `OutboxPersistenceTest` (2): the locking poll query on H2, order, skipping published rows; the purge cut-off.
- `DoubleBookingGuardTest` (3): runs **without** a test transaction so each save commits like a separate request. Covers a second open session on the connector, then on the vehicle, and that a stopped session frees both.
- `TransactionalDeclinedPaymentRecorderTest` (1): save then publish, in one transaction.
- Payment, charging, controller and domain tests:
  - collect with a new card, still failing, nothing owed
  - `payOutstanding` → `STOPPED`, 402 with nothing changed, 409
  - owner-only pay
  - `recovered` only from `FAILED`

## End-to-end verification

On the Docker Compose stack, 2026-11-23:
- **Migrations:** V13 and V14 applied on Postgres over the existing data: `ux_charging_sessions_open_connector`, `ux_charging_sessions_open_vehicle`, `ex_reservations_charger_window`. 0 `ERROR` lines at startup.
- **`scripts/demo-flow.ps1`:** **all 82 checks** passed, with every event going through the outbox: 35 rows, 0 left unpublished, each sent on its first attempt. The "three trace ids" audit check still passes, so the relay kept the request link.
- **The race was decided by the database, not by luck:**
  ```
  == 15. Two drivers grab the same free connector at the same instant
    results 201, 409
    [PASS] exactly one wins (201) and the other gets 409 Connector in use
  postgres: ERROR: duplicate key value violates unique constraint "ux_charging_sessions_open_connector"
  ```
- **Kafka stopped mid-run:**
  ```
  with Kafka DOWN: start -> ACTIVE in 105 ms; stop -> STOPPED GBP 5.00 in 51 ms
  pending while down: 5
  pending after Kafka is back: 0
  audit for the session made while Kafka was down: PaymentAuthorized > Requested > Started > PaymentCaptured > Stopped
  trace ids on them: 2 distinct, none missing
  ```
  Before the outbox, the same request would have blocked for up to `max.block.ms` (60 s) and then failed.
- **Dave pays later:** 402 with the failed card; after switching to `tok_visa`, `STOPPED` and `CAPTURED` for exactly the £5.00 owed; the report's `outstanding` is back to 0; he gets a "payment received" notification.

## Requirements status

| | |
|---|---|
| NFR-3 at-least-once + idempotent handlers | ✅ outbox + `eventId` dedupe everywhere |
| Double-booking under concurrency | ✅ database-enforced for sessions and reservations (Postgres) |
| Unpaid sessions | ✅ the driver can pay later |

## Still open (needs outside services, or is beyond this platform's current scope)

1. **A real payment provider** (and reconciliation webhooks), behind `PaymentProvider`.
2. **Real email or push**, behind `NotificationSender`.
3. **Time-of-day tariffs and idle fees.**
4. **Search by operator company.** Stations have no operator field.
5. **Production Keycloak** (Postgres, TLS, real hostname) and **outbox metrics** (pending count and age as a health indicator).
6. Releasing holds at a real provider when a start fails after authorization. The simulated provider has no real hold.
