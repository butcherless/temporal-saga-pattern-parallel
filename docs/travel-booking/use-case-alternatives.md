# Use Case: Complete a Travel Booking — Alternative Flows

> Flows that reach the same successful outcome as the [main scenario](./use-case-main.md) (booking ends **Confirmed**) but diverge from its path without being a failure. Terms defined in [`business-definitions.md`](./business-definitions.md). Failure/compensation flows live in [`use-case-errors.md`](./use-case-errors.md) instead — not here. Status: **PENDING — not yet reviewed against the main case's current terminology (e.g. "offer reference" vs. the earlier "business key" phrasing) or its current 8-step numbering. Treat this doc as stale until reconciled.**

## A1 — Idempotent duplicate submission

**Trigger:** the operator resubmits a booking request with a `businessKey` that already has a booking associated with it — whether the earlier saga is still in flight (**Pending**) or already finished (**Confirmed**).

**Flow:**
1. The orchestrator resolves the `businessKey` and finds an existing booking rather than minting a new `sagaId`.
2. No new reservations, holds, confirmations, or captures are issued.
3. The orchestrator returns the existing booking's current state (whatever it is — `Pending` or `Confirmed`) to the caller.

**Outcome:** identical to whatever the original submission's outcome is/was — this flow never produces a second saga for the same `businessKey`.

**Grounding:** direct analogue of `temporal-saga-pattern`'s implemented "idempotent duplicate order submission" reference scenario (see that repo's `CLAUDE.md`), and consistent with the idempotency-by-`businessKey` discipline both sibling repos already commit to.

## A2 — Status query

**Trigger:** a client asks for the current status of a booking by its `sagaId` (in any state — `Pending`, `Confirmed`, `Cancelled`, or `Failed`).

**Flow:** `booking-service` returns the `travel_booking` row's own current status, read-only, without touching the running saga.

**Status:** **implemented** — `GET /travel-bookings/{sagaId}` on `BookingController`, backed by
`TravelBookingProgressionService#findBooking`. Deliberate scoping decision: it reports only the
**coarse booking status** (`PENDING`/`CONFIRMED`/`CANCELLED`/`FAILED`, per
[definitions §7](./business-definitions.md#7-booking-status--lifecycle)), the same
`{sagaId, status}` shape the buy and cancel operations already return. It does **not** surface
live per-leg progress ("which legs are reserved/confirmed so far") — that would need either a
Temporal `@QueryMethod` on the Workflow (as `temporal-saga-pattern`'s `GET /orders/{sagaId}` does)
or `booking-service` fanning out to the leg services, neither of which is warranted while the
Workflow tracks no queryable sub-state. `404` (RFC 9457 `ProblemDetail`) when no booking exists
for the `sagaId`. See [`controller-layer-plan.md`](./controller-layer-plan.md) Operation 3.

## Note on scope

Only two candidate alternative flows are identified, and only because both have a direct precedent already implemented in `temporal-saga-pattern`. This list is intentionally not padded out with speculative flows (e.g. partial package changes, leg substitution, multi-currency handling) that have no grounding in the current business case or source docs. Additions here should come from an actual product requirement, not from filling out the section for its own sake.
