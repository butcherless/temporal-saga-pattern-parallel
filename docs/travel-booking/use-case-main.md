# Use Case: Complete a Travel Booking — Main Success Scenario

> Terms used below (`sagaId`, leg, hold/capture, etc.) are defined once in [`business-definitions.md`](./business-definitions.md) — not repeated here. Sibling docs: [alternative flows](./use-case-alternatives.md), [error/compensation flows](./use-case-errors.md) — both **pending**, not yet reconciled with this doc's current terminology. Status: **draft, for discussion**.

## Actors

Operator/agency, booking orchestrator, flight/hotel/car services, payment service — see [definitions §2](./business-definitions.md#2-actors).

## Preconditions

- For each of the three legs, the operator holds a previously-obtained **offer reference** (UUID, valid 10 minutes from issuance) from that provider (hotel, car rental, airline). The leg's price is bound to that offer reference at the provider and never enters this saga as a separate value — see [definitions §5](./business-definitions.md#5-money--pricing).
- The customer holds a **payment authorization token**, generated outside this saga for the total booking price — see [definitions §4](./business-definitions.md#4-identifiers) for how that total was determined. The operator supplies the token with the booking request; this saga uses its amount as-is and performs no price computation of its own.
- No prior booking exists for the given `businessKey`.
- All amounts are in **EUR**.

## Trigger

The operator submits a booking request: the intent to buy the whole bundle against the three already-made offers, at the amount the payment authorization token already authorizes.

## Main flow

1. **Booking requested.** The orchestrator mints a `sagaId`, resolves the `businessKey`, and records the booking as **Pending**, storing the three offer references and the payment authorization token.
2. **Reservations, in parallel.** The orchestrator asks all three leg services to reserve their resource at the same time — none needs to wait on another. Each reservation call names only the offer reference being honored; no price travels in either direction, since price is already bound to that offer reference at the provider. Each leg service reserves its resource (seat, room, car) and reports back success.
3. **All three reservations confirmed.** The orchestrator waits until flight, hotel, and car have all reported success. Only once all three are in is the booking ready for payment.
4. **Payment held.** The orchestrator asks the payment service to hold (not yet charge) the amount the payment authorization token already authorizes — no computation, the token's amount is used as-is.
5. **Confirmations, in parallel.** With funds held, the orchestrator asks all three leg services to confirm their reservation — again, none needs to wait on another.
6. **All three confirmations done.** The orchestrator waits until flight, hotel, and car have all confirmed.
7. **Payment captured.** The orchestrator asks the payment service to capture (actually charge) the held amount.
8. **Booking completed.** The orchestrator marks the booking **Confirmed**. Terminal state.

## Postconditions

- Booking status is **Confirmed**.
- Flight, hotel, and car reservations are all confirmed with their respective services.
- Payment has been captured, in EUR, for the amount the payment authorization token authorized.

## Operations & identifiers per step

| # | Step | Operation(s) | Identifiers involved |
|---|---|---|---|
| 1 | Booking requested | *Create booking* | `sagaId` (minted), `businessKey` (resolved), `operatorId` (supplied), one offer reference (UUID) per leg (supplied), payment authorization token (supplied) |
| 2 | Reserve flight | *Reserve flight* | `sagaId`, flight details, flight offer reference (UUID) — no price |
| 2 | Reserve hotel | *Reserve hotel* | `sagaId`, hotel details, hotel offer reference (UUID) — no price |
| 2 | Reserve car | *Reserve car* | `sagaId`, car details, car offer reference (UUID) — no price |
| 3 | Wait for all three | *(orchestration only)* | — |
| 4 | Hold payment | *Hold payment* | `sagaId`, payment authorization token — no separate amount, no computation |
| 5 | Confirm flight/hotel/car | *Confirm flight* / *Confirm hotel* / *Confirm car* | `sagaId` only |
| 6 | Wait for all three | *(orchestration only)* | — |
| 7 | Capture payment | *Capture payment* | `sagaId` |
| 8 | Booking completed | *Confirm booking* | `sagaId` |

## Idempotency

Every operation below must be safe to call more than once for the same `sagaId` — the orchestrator (or whatever executes it) may retry a call whose outcome it doesn't know (e.g. after a timeout), and a retry must never produce a duplicate side effect:

| Operation | Idempotency key | What a duplicate call must do |
|---|---|---|
| Create booking (step 1) | `businessKey` | Return the existing booking rather than creating a second one (see [alternatives](./use-case-alternatives.md), A1) |
| Reserve flight/hotel/car (step 2) | `sagaId` | Recognize this `sagaId` was already reserved against that leg's offer reference and return the same result, without reserving the resource a second time |
| Confirm flight/hotel/car (step 5) | `sagaId` | Recognize this `sagaId` was already confirmed and return the same result, without double-confirming |
| Hold payment (step 4) | `sagaId` | Recognize a hold already exists for this `sagaId` and return it, without placing a second hold |
| Capture payment (step 7) | `sagaId` | Recognize the capture already happened for this `sagaId` and return it, without charging twice |
| Confirm booking (step 8) | `sagaId` | Recognize the booking is already **Confirmed** and return that, without re-processing completion |

This requires every leg service and the payment service to persist enough state, keyed by `sagaId`, to recognize a repeat call and short-circuit to the original result — not just tolerate a second attempt as if it were new.

## Readiness notes — open before this can be implemented

1. **REST/controller layer** — not yet defined for `booking-service`, `flight-service`, `hotel-service`, `car-service`, or `payment-service`. Every operation in the tables above needs a concrete HTTP shape (method, path, request/response body, status code) — the natural next step once this doc and the definitions doc are agreed.
