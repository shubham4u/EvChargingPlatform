# Event Catalog

## Event Design Rules

- Every event has a stable name, version, event ID, correlation ID, and timestamp.
- Consumers must be idempotent.
- Events are published after the local transaction commits.
- Schema evolution must be backward compatible whenever possible.

## Canonical Envelope

```json
{
  "eventId": "uuid",
  "eventType": "ChargingSessionStarted",
  "eventVersion": 1,
  "correlationId": "uuid",
  "causationId": "uuid",
  "occurredAt": "2026-07-29T10:15:30Z",
  "producer": "charging-session-service",
  "payload": {}
}
```

## Core Events

| Event | Producer | Typical Consumers | Purpose |
|---|---|---|---|
| StationRegistered | Catalog Service | Search, reporting, notification | A new station becomes available |
| StationUpdated | Catalog Service | Search, reporting | Static station data changed |
| ConnectorAvailabilityChanged | Availability Service | Search, reservation, reporting | Live connector state changed |
| ReservationCreated | Reservation Service | Session, notification, reporting | A reservation was placed |
| ReservationExpired | Reservation Service | Session, notification | Reservation hold ended |
| ChargingSessionRequested | Charging Session Service | Payment, gateway, reporting | User requested session start |
| ChargingSessionStarted | Charging Session Service | Payment, notification, reporting | Session was accepted and started |
| MeterValueRecorded | Charging Session Service | Pricing, reporting | New metering data arrived |
| ChargingSessionStopped | Charging Session Service | Payment, notification, reporting | Session ended |
| PriceCalculated | Pricing Service | Session, payment, reporting | A cost estimate or final price was produced |
| PaymentAuthorized | Payment Service | Session, reporting | Funds were reserved |
| PaymentCaptured | Payment Service | Reporting, notification | Funds were collected |
| PaymentFailed | Payment Service | Session, notification, reporting | Payment workflow failed |
| RefundIssued | Payment Service | Reporting, support | Money was refunded |
| NotificationRequested | Any domain service | Notification Service | Trigger message delivery |
| StationFaultDetected | Charge Point Gateway | Admin, notification, reporting | Station reported a problem |

## Example Event Flow

1. A user starts a charging session.
2. The session service validates state and writes its local transaction.
3. The session service publishes `ChargingSessionRequested`.
4. The payment service authorizes the payment and publishes `PaymentAuthorized`.
5. The session service receives the event and transitions the session to active.
6. The notification service sends a confirmation message.

## Reliability Rules

- Use the outbox pattern for event publication.
- Use consumer-side deduplication with Redis or local persistence.
- Keep event payloads compact and domain-focused.
- Do not rely on event ordering across unrelated aggregates.

