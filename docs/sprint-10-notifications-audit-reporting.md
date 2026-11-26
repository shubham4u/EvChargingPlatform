# Sprint 10 — Notifications, Audit and Reporting

> Status: **done**. Written after the work. Stacked on Sprint 9 (`feature/sprint-9-pricing-payments`, not yet merged), so merge that first.

## Goal

Close the remaining functional requirements:
- **FR-8 Notifications:** reservation changes, session events, failed payments.
- **FR-10 Audit and reporting.**
- **Acceptance criterion 5:** "Operators can trace every major business action from API request to persisted event."

## Decisions

| Question | Decision | Why |
|---|---|---|
| How notifications are triggered | Only from Kafka events, in their own consumer group (`notifications`); one class-level listener per topic | Notifying can never slow down or break charging, and the module could become its own service unchanged |
| Duplicates | Inbox rows are unique on the source `eventId` | Kafka delivers at least once (NFR-3) |
| Who the payment event is for | Added `userId` to the four payment event contracts (an additive field) | Looking the payment up instead could race the producer's commit, because events are still published before commit |
| Email or push | `NotificationSender` port; a logging stand-in for now | No provider is available here; the inbox is the record either way |
| What the audit stores | Every event on every `*.events.v1` topic, **exactly as published**, plus type, key, Kafka position and **trace id** | The history stays true as contracts evolve |
| How the audit reads Kafka | A raw `String` listener on a topic pattern (`@KafkaListener(properties = value.deserializer=…)`) in its own group | It needs the original JSON and the `__TypeId__` and `traceparent` headers, which the typed listeners hide |
| Linking an event to its request | Turned on Kafka observation (`spring.kafka.template/listener.observation-enabled`) so the W3C `traceparent` travels with each event; the audit stores the trace id | Acceptance criterion 5: from a trace id, Jaeger shows the HTTP request and every event it published |
| Reports | Computed from the owning modules' query use cases | Correct to the second and simple at this scale; a separate analytics read model is the next step when it isn't |

## What was built

- **`notifications`:**
  - `Notification` (types: reservation confirmed, cancelled or expired; charging started or finished; payment received; card declined; payment failed; refund issued)
  - `NotificationService`, three Kafka listeners, `LoggingNotificationSender`
  - `GET /notifications[?unread=true]`, `PATCH /notifications/{id}/read` (recipient only, not even operators)
- **`audit`:**
  - `AuditEvent`, `AuditService`, `KafkaAuditListener`
  - `GET /audit/events?key=&type=&traceId=&limit=` (operator)
- **`reporting`:**
  - `GET /reports/stations/{id}?from=&to=` (operator): sessions, active, paid, payment failures, kWh, billed, revenue (net of refunds), refunded, outstanding
  - new queries: sessions by station and period; payments by sessions
- **Migration `V12`:** `notifications`, `audit_events` (payload `VARCHAR(8000)`: Postgres reports `TEXT` as VARCHAR but H2 reports it as CLOB, which would fail Hibernate's schema validation), and the indexes.
- **Config:**
  - Kafka trace propagation on
  - responses declare `charset=UTF-8`
- **Demo:** step 13, 74 checks in total.

## Bugs found along the way

1. **Audit timestamps lost nanoseconds** (`.525786629` → `.525786600`). Jackson parsed the JSON number as a `double`, which can't hold 19 digits. Found by a unit test; fixed by reading floats as `BigDecimal`.
2. **"£" showed as "Â£" in Windows PowerShell 5.1.** The stored bytes were correct UTF-8 (`c2 a3`), but the response said only `application/json`, and PowerShell 5.1 then assumes Latin-1. The responses now declare `charset=UTF-8`, verified on the stack.
3. **Jaeger "trace not found"** was span export batching: I queried about 3 seconds after the request. Retried later, both traces were there, starting at the HTTP request.
4. **Demo script error:** `Select-Object -First 1` on a PowerShell 5.1 JSON array returned the whole array, which made an invalid URL. The array is now unrolled first.
5. **A doc file damaged by my own tooling:** a PowerShell 5.1 `Get-Content`/`Set-Content` edit added a BOM and garbled 4 lines in `api-smoke-test.md`. Checked line by line against git; the file ended clean, with only the intended edits. No more PowerShell file writes.

## Tests

**263 tests, 0 failures, 0 errors — BUILD SUCCESS** (240 → 263).

| Test class | Tests | Covers |
|---|---|---|
| `NotificationServiceTest` | 7 | £ from minor units, declined vs failed capture, reservation and charging wording, dedupe, skip events without a user, mark read, unknown currency |
| `AuditServiceTest` | 6 | id, type, key, nanosecond `occurredAt` and trace id from a raw record; ISO times and no trace; dedupe; stable id without `eventId`; traceparent parsing; query normalisation |
| `ReportingServiceTest` | 3 | revenue = captured − refunded, outstanding = failed sessions' cost, active count; empty period; backwards period |
| `NotificationControllerTest` | 3 | inbox (UTF-8 body), 401, only the recipient marks read |
| `OperatorEndpointsTest` | 2 | audit and report for operators, 403/401 otherwise, parsed payload, default period |
| `AuditAndNotificationPersistenceTest` | 2 | audit search with every combination of optional filters and occurrence order on H2 with V1–V12; inbox order, unread filter, seen events |

## End-to-end verification

On the Docker Compose stack, 2026-11-23:

- **Startup:** V12 applied, 0 `ERROR` lines. The new consumers replayed the history: **221** audit rows, none with a trace id (correct: published before tracing was on), and **88** notifications (deduped, and skipping payment events from before `userId` existed).
- **Tracing:** after a demo run, **all 29** new events carried a trace id.
- **Acceptance criterion 5, end to end:**
  1. The audit for one session lists `PaymentAuthorized`, `Requested` and `Started` under one trace, and `PaymentCaptured` and `Stopped` under another.
  2. In Jaeger (`/api/traces/<id>`), the first trace starts at **`http post /charging/sessions`** (17 spans: security, the 3 event sends, the consumers).
  3. The second starts at **`http patch /charging/sessions/{id}/stop`**.
- **Demo:** `scripts/demo-flow.ps1` passed **all 74 checks**:

```
== 13. Everyone is told, and everything is on the record
  [PASS] Alice's inbox: reservation confirmed, charging finished, payment received, refund issued
  [PASS] Carol was told her card was declined
  [PASS] Dave was told his payment failed and to update his card
  [PASS] marking one read leaves one fewer unread
  [PASS] Bob marking Alice's notification read -> 403 Forbidden
  [PASS] the audit trail for Alice's session has all 6 events, each with a trace id
  audit   PaymentAuthorizedEvent > ChargingSessionRequestedEvent > ChargingSessionStartedEvent > PaymentCapturedEvent > ChargingSessionStoppedEvent > PaymentRefundedEvent
  [PASS] start, stop and refund are three requests, so three trace ids
  report  3 sessions, 60.25 kWh, revenue 3.88, refunded 19.74, outstanding 5.00 GBP
  [PASS] report: revenue = Bob's bill, refunded = Alice's, outstanding = Dave's
All 74 checks passed.
```

## Requirements status

| | After Sprint 10 |
|---|---|
| FR-8 Notifications | ✅ reservation, session and payment events; 🟡 no real email or push provider; operational incidents need fault events nothing produces yet |
| FR-10 Audit and reporting | ✅ full event history with trace links, station reports |
| Acceptance 5: trace a business action from request to event | ✅ verified in Jaeger |

## Open items

1. **A real delivery channel** (email, push) behind `NotificationSender`, using the user's email from the identity module.
2. **Operational-incident notifications** (FR-8): `StationFaultDetectedEvent` exists, but nothing raises it yet (Maintenance context).
3. **Audit retention and payload size:** payloads are capped at 8,000 characters (the current events are well under 1 KB); add a retention policy.
4. Carried over, for Sprint 11 (reliability): a transactional outbox, so events go out only after commit; a database guard against double-booking; releasing holds; collecting from `PAYMENT_FAILED` sessions.
