# Sprint 9 — Pricing and Payments

> Status: **done**. Written after the work. Builds on Sprint 8 (merged as PR #12). It also carries the `POST /users/me` fix found while hand-testing Sprint 8.

## Goal

Charge drivers for what they use (FR-6 pricing, FR-7 payments), and meet acceptance criterion 3: **"A payment failure does not silently complete a charging session."**

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where prices live | New `pricing` module; tariff per station: fee per session + per kWh + per minute, ISO 4217 currency | `microservices.md` lists a Pricing Service for tariff calculation |
| Price changes | Tariffs are immutable versions; a session stores the `tariffId` in force at start and is billed at it | A price change mid-charge must not change a bill |
| Rounding | Components summed at full precision; the total is rounded once, half up, to 2 dp | Rounding each part first can be a penny out (covered by a test) |
| Unpriced station | Start refused, `404 Tariff not found`, with how to set one | Never charge for free by accident |
| Real card processing | `PaymentProvider` port + **simulated provider** with Stripe-style test tokens | None is available here. A real provider means one new adapter. |
| Card data | Only the provider token, brand and last 4 are stored; the token is never returned by the API | Card numbers must not touch the platform |
| When money moves | **Hold** (£50, `app.payments.hold-amount`) when charging starts; **capture** the priced cost on stop; operator **refund** | The standard pre-authorization pattern for EV charging |
| Hold order | Authorized **before** the session is saved or its "requested" event published | A declined card leaves nothing behind except the payment record |
| Declined hold | Start refused, `402 Payment declined` (with `failureCode`); the decline is recorded in its **own transaction** (`REQUIRES_NEW`) | The session's transaction rolls back, but support must still see the decline |
| Failed capture | The capture returns `FAILED` rather than throwing; the session ends **`PAYMENT_FAILED`** with `totalCost` set, and the connector is freed | Acceptance criterion 3. Throwing would roll back the stop and leave the car "charging". |
| Retried capture | A second capture returns the settled payment | A retried stop must never charge twice |

## What was built

- **`pricing`:**
  - `Tariff` (define, `priceFor`) and `Price`, with full breakdown
  - `PUT /stations/{id}/tariff` (operator) and public `GET /stations/{id}/tariff`
  - `PriceSessionUseCase`
- **`payments`:**
  - `PaymentIntent`: `AUTHORIZED → CAPTURED → REFUNDED`, or `FAILED`
  - `PaymentMethod`
  - `PaymentApplicationService`, `TransactionalDeclinedPaymentRecorder`, `SimulatedPaymentProvider`, `KafkaPaymentEventPublisher`
  - Endpoints: `PUT/GET /payments/methods/me`, `GET /payments`, `GET /payments/{id}`, `POST /payments/{id}/refund` (operator)
- **Charging:**
  - the tariff is locked in, and the hold taken, at start; pricing and capture at stop
  - new status `PAYMENT_FAILED`
  - the response gains `tariffId`, `totalCost` and `currency`
  - sessions started before this sprint (no tariff) stop unpriced, as they started
- **Events:**
  - `payment.events.v1`: `PaymentAuthorized`, `PaymentCaptured`, `PaymentFailed`, and the new **`PaymentRefundedEvent`** contract
  - keyed by session id, with amounts in minor units
- **Migrations:**
  - `V10`: pricing columns on `charging_sessions`
  - `V11`: `tariffs`, `payment_methods`, `payment_intents` (`session_id` unique, `@Version`)
- **Also included:** `POST /users/me` now accepts every body-less style, including `Content-Type: application/json` with nothing after it.

## Bugs and mistakes along the way

1. **`POST /users/me` with a JSON header and no body returned 400** (from Sprint 7). Found by hand-testing Sprint 8. The fix uses one mapping that reads the raw body. Tests now cover all five client styles.
2. **Mockito can't mock concrete classes on Java 25.** `DeclinedPaymentRecorder` became an interface with a transactional implementation, which is a cleaner seam anyway.
3. **Two mistakes in my own new tests,** caught by the IDE before running: a station factory class that doesn't exist, and a meaningless `verify` line. Both were replaced with correct code.
4. **Name clash.** One adapter implemented two ports that both declared `findByUserId` with different return types. The card lookup was renamed `findForUser`.

## Tests

**240 tests, 0 failures, 0 errors — BUILD SUCCESS** (201 → 240, the first number including the carried-over fix).

| Test class | Tests | Covers |
|---|---|---|
| `TariffTest` | 4 | 0.50 + 42.75 kWh × 0.45 + 30 min × 0.05 = 21.24; rounding once; part-minutes; validation |
| `PricingApplicationServiceTest` | 4 | station check, how-to message, priced at the given tariff not the current one |
| `PaymentIntentTest` | 4 | every transition; can't capture more than the hold, twice, or refund a failure |
| `PaymentApplicationServiceTest` | 10 | against the real simulated provider: card save, hold, no card, decline recorded separately (not in the rolled-back transaction), capture, failed capture, over-hold, idempotent capture, refund, refuse refund |
| `TariffControllerTest` | 3 | public read, operator write records who, 401/403/400 |
| `PaymentControllerTest` | 5 | token never echoed, 400/404, list scoping, 403, operator-only refund and 409 |
| `PaymentPersistenceTest` | 3 | intent lifecycle across persistence contexts, card replace, tariff versions (H2, real V1–V11) |
| `ChargingSessionApplicationServiceTest` | 29 (+6) | hold before save, declined → nothing saved, no tariff → card untouched, priced and captured, failed capture → `PAYMENT_FAILED`, legacy unpriced stop |
| `UserControllerTest` | 10 (+2) | all body-less styles, bad body |

## End-to-end verification

On the Docker Compose stack, 2026-11-23: V10 and V11 applied, 0 `ERROR` lines at startup. `scripts/demo-flow.ps1` passed **all 63 checks** (up from 43), first run:

```
== 9. Alice unplugs
  bill    GBP 19.74
  [PASS] billed GBP 19.74-19.80 (fee + energy + time at the tariff it started under)
  [PASS] her card was charged exactly the bill
== 11. Payment problems are never silent
  [PASS] Carol's declined card stops the session starting -> 402 Payment declined
  [PASS] no session was created for Carol
  [PASS] but the decline is on her record (FAILED, card_declined)
  [PASS] the connector is still AVAILABLE
  session 6dd610b8-…  PAYMENT_FAILED  owes GBP 5.00
  [PASS] when the final charge fails, the session ends PAYMENT_FAILED, not STOPPED
  [PASS] his payment is FAILED (capture_failed)
  [PASS] his connector is freed anyway
  [PASS] charging at a station with no prices set -> 404 Tariff not found
== 12. The operator refunds Alice
  [PASS] Alice refunding herself -> 403 Forbidden
  [PASS] Olivia refunds Alice's payment
  [PASS] refunding it twice -> 409 / refunding Dave's failed payment -> 409
All 63 checks passed.
```

Database after the run: Carol's `card_declined` intent exists, and its session id has **0** rows in `charging_sessions`. That proves the decline record survived the rolled-back start.

```
 status   | failure_code   | authorized_amount | captured_amount | session_rows
 FAILED   | capture_failed |             50.00 |                 |            1
 FAILED   | card_declined  |             50.00 |                 |            0
 CAPTURED |                |             50.00 |            3.88 |            1
 REFUNDED |                |             50.00 |           19.74 |            1
```

`payment.events.v1` held exactly the expected 8 events: Authorized ×3 (Alice, Bob, Dave), Captured ×2, Failed ×2 (Carol's decline, Dave's capture), Refunded ×1. Amounts are in minor units, e.g. `{"amountMinor":388,"currency":"GBP"}`.

The updated manual steps in `docs/api-smoke-test.md` were run as written: tariff set, card saved, charged GBP 19.74, payment `CAPTURED` 19.74.

## Requirements status after this sprint

| | Before | After |
|---|---|---|
| FR-6 Pricing | ❌ | ✅ tariffs with fee, energy and time; versioned. Not yet: time-of-day windows. |
| FR-7 Payments | ❌ | 🟡 authorize, capture, refund. Missing: reconciliation with provider callbacks, and a real provider. |
| Acceptance 3: payment failure isn't silent | ❌ | ✅ |

## Open items

1. **Collecting money from `PAYMENT_FAILED` sessions.** Needs a retry or "update your card and pay" flow, and an operator view of what's owed.
2. **Sessions costing more than the hold** fail capture (`amount_exceeds_authorization`). Real fix: incremental authorization, or a hold sized from connector power × typical duration.
3. **Time-of-day tariffs** (FR-6 "time windows") and idle fees.
4. **Reconciliation** (FR-7): provider webhooks and a nightly match of intents against provider records.
5. **Release the hold** when a session ends at £0, or when a start fails after authorization. The simulated provider has no real hold, but a real one would.
6. **Notifications and audit/reporting** (FR-8, FR-10) are next.
7. Carried over: database-level double-booking guard; events published before commit (outbox).
