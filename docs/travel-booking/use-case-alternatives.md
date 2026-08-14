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

## A2 — Status query while the booking is in flight

**Trigger:** a client asks for the current status of a booking whose `sagaId`/`businessKey` is still **Pending**.

**Flow:** the orchestrator reports the booking's current progress (e.g. which legs are reserved/confirmed so far) without altering the saga.

**Status:** **not yet decided whether this applies to the travel-booking case.** Flagged here only because `temporal-saga-pattern` implements an analogous query (`GET /orders/{sagaId}` via a Temporal `@QueryMethod`, per that repo's `CLAUDE.md`) — worth a deliberate yes/no rather than a silent gap, not a committed requirement yet.

## Note on scope

Only two candidate alternative flows are identified, and only because both have a direct precedent already implemented in `temporal-saga-pattern`. This list is intentionally not padded out with speculative flows (e.g. partial package changes, leg substitution, multi-currency handling) that have no grounding in the current business case or source docs. Additions here should come from an actual product requirement, not from filling out the section for its own sake.
