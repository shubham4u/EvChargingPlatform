# Task 3.1 — Reservation Domain Model

The Reservation bounded context includes immutable identifier value objects, domain-specific lifecycle exceptions, and version-one immutable lifecycle events. The existing `Reservation` aggregate remains the owner of lifecycle transitions; application and adapter layers must not duplicate its rules.
