# Travel Booking Saga — Business Definitions

> Canonical business vocabulary for the travel-booking case. Every other doc in this set — [`use-case-main.md`](./use-case-main.md), [`use-case-alternatives.md`](./use-case-alternatives.md), [`use-case-errors.md`](./use-case-errors.md) — assumes these terms rather than redefining them. Status: **draft, for discussion**.

## 1. Business case

A travel agency/operator books a package on behalf of a customer, combining three independently-bookable resources — flight, hotel, car — under one commercial transaction. The three legs don't depend on each other's data (booking a flight doesn't need to know the hotel room number), but they share a **commercial dependency**: the customer is charged once, for the sum of all three, and only after all three resources are actually reserved. That shared payment step is what makes this case interesting relative to a fully independent fan-out: it reintroduces a real ordering constraint, just not a chain between the three legs themselves.

This saga does **not** cover price discovery. By the time it starts, the operator already holds a separate, previously-obtained **offer** from each of the three providers (hotel, car rental, airline) — a binding price quote issued outside this saga. What this saga's opening operation (booking creation) represents is the **intent to buy** the whole bundle at those already-offered prices — a commit-to-purchase step, not a search/quote step. Each leg's reservation call carries forward the specific offer being honored (§4, §6) rather than asking the provider to price the resource fresh.

### Why an orchestrator, not choreography

With three parallel bookable resources plus a payment step (and a compensation for each), services simply reacting to each other's events makes it hard to answer a basic operational question: "where does this booking currently stand?" This case is modeled with an explicit orchestrator instead, for three reasons:

- Full visibility into where a given booking currently stands, at any point in the flow.
- Centralized decision-making for what to compensate, and when, rather than each service guessing from the events it happens to see.
- Easier to expose to operations/support tooling — one place to ask "what's the state of `sagaId` X," not one per service.

## 2. Actors

| Actor | Role |
|---|---|
| **Operator / agency** | Assembles the package on behalf of a customer and submits the booking request. Identified by `operatorId` — pure business metadata, not part of any idempotency mechanism (see §4). |
| **Booking orchestrator** | Owns the saga end to end: creates the booking record, coordinates every step below, is the thing a client polls/queries for status. |
| **Flight, Hotel, Car services** | Each owns one leg's reservation lifecycle: reserve, then (later) confirm. |
| **Payment service** | Owns hold and capture, and, on failure, refund. |

## 3. Glossary

| Term | Meaning |
|---|---|
| **Leg** | One of the three independently-bookable resources: flight, hotel, car. |
| **Reservation** | The first-phase call for a leg — holds the resource (seat, room, car) without yet being commercially final. |
| **Confirmation** | The second-phase call for a leg, made only after payment is held — finalizes the reservation. |
| **Hold** (*retención*) | Reserve funds against the customer's payment method without capturing them. Reversible, typically instant, no real money movement to undo. |
| **Capture** (*captura*) | Convert a hold into an actual charge. Once captured, undoing it is a **refund**, not a hold release. |
| **Refund** (*reintegro*) | Return previously captured funds. Asynchronous against a real payment gateway; can fail; may take days to settle — a true compensating transaction, not a reversible in-memory op. |
| **Compensation** | The undo action for a step that already succeeded, triggered when a later step in the same saga fails. |
| **Orchestrator / Workflow** | The process that owns the saga's state and sequencing end to end. |
| **Idempotency** | Every operation — reservation, confirmation, cancellation, hold, capture, refund, booking creation — must tolerate being invoked more than once with the same identifiers without double-booking or double-charging: booking creation keys off `businessKey`, every other operation keys off `sagaId`. A repeat call must return the original result, not repeat the side effect. See [main use case](./use-case-main.md), Idempotency. |

## 4. Identifiers

| Identifier | Meaning | Status |
|---|---|---|
| **`sagaId`** | Global correlation identifier for one booking attempt, minted when the booking is created. Every command/event/activity in the flow carries it. | Defined |
| **`businessKey`** | Client-supplied-or-generated idempotency key for the booking as a whole, independent of `sagaId`. Resolving a duplicate `businessKey` should return the existing booking rather than starting a second one (see [`use-case-alternatives.md`](./use-case-alternatives.md) A1). | Defined |
| **`operatorId`** | Which agency assembled the package. Business metadata only — doesn't gate uniqueness, isn't validated against any operator registry. | Defined |
| **Leg resource identifier** (PNR, room confirmation number, rental agreement id) | Whatever a leg service uses internally to track its own reservation between the reserve and confirm calls. | **Not required to travel back to the orchestrator** — each leg service is expected to track its own record keyed by `sagaId` alone. Confirmed assumption, not an oversight — see [`use-case-main.md`](./use-case-main.md), Idempotency. |
| **Offer reference** | Per leg, a **UUID** identifying a previously-obtained, binding offer from that provider (hotel, car rental, airline), valid for **10 minutes from issuance**. Not called "business key" — that term is already `businessKey` (above), a different, booking-level concept; using it for both would collide. Price is bound to this reference at the provider, not carried separately by this saga — see §5. A reservation reaching a provider after the reference has expired is an ordinary failure (see [error scenarios](./use-case-errors.md), E1 — pending sync), not a special case needing its own mechanism. | Defined. |
| **Payment authorization token** | A single opaque token, generated by the **customer** in a step outside this saga (not by the operator or the orchestrator) — the customer's authorization to hold or charge the total booking price. That prior step is where the total was first known: the customer browsed products, picked the three that became the offers referenced in this booking, saw the resulting total, and only then had the token generated for that amount — so the token's authorized amount is fixed *before* this saga starts, and this saga uses it as-is, with no computation of its own. Supplied once per booking at booking-request time. The underlying payment method is either **card** or **digital wallet** — exactly two supported types — but that's a property of what the token was issued against, not a separate field this saga carries: the token is the only credential that travels through the flow. | Defined. |
| **Currency** | ISO currency code for every money amount (leg price, hold/capture/refund amount). | Defined — fixed at **EUR** for every amount in this saga; no multi-currency support. |

## 5. Money & pricing

| Concept | Meaning | Status |
|---|---|---|
| **Leg price** | The cost of one leg (flight/hotel/car), in EUR. Bound directly to that leg's offer reference (§4) at the provider (e.g. offer `<uuid>` = €500) — **not a value this saga ever carries, sums, or recalculates**. The reservation and confirmation calls (main use case, steps 2/5) pass the offer reference only; no price field travels in either direction. | Defined. |
| **Total amount** | The amount to hold, then capture — in EUR. **Not computed by this saga at all**: it's simply whatever the payment authorization token (§4) already authorizes. The saga uses that amount as-is. | Defined. |

## 6. Booking request fields

What the operator supplies at booking-request time:

| Scope | Fields |
|---|---|
| **Booking-level** | `operatorId`, `businessKey`, payment authorization token (see §4) |
| **Flight leg** | Flight number, origin airport, destination airport, departure date, passenger count, offer reference (UUID) |
| **Hotel leg** | Hotel code, room type, check-in date, check-out date, guest count, offer reference (UUID) |
| **Car leg** | Car category, pickup location, pickup date, dropoff date, offer reference (UUID) |

Each leg's fields are disjoint from the others — there's no shared field set across flight/hotel/car beyond the identifiers in §4. None of the three legs' fields include a price — see §5.

## 7. Booking status & lifecycle

Top-level status, used by both use-case docs:

| Status | Meaning |
|---|---|
| **Pending** | Booking created; saga in flight — reservations and/or payment not yet fully settled. |
| **Confirmed** | Terminal success — all three legs confirmed, payment captured. See [main use case](./use-case-main.md). |
| **Failed** | Terminal — saga aborted before payment capture due to a step failure, all successful reservations compensated, no money captured. See [error scenarios](./use-case-errors.md). |
| **Cancelled** | Terminal — booking was explicitly cancelled by the operator/customer before reaching `Confirmed`, all successful reservations compensated, no money captured. Distinct from `Failed`: same underlying compensation mechanics, but triggered by a deliberate request rather than a step failure. Only reachable while the booking is `Pending`; a booking that's already `Confirmed` cannot be cancelled through this path — see [controller layer plan](./controller-layer-plan.md). |

**Pending** covers several distinct sub-states worth naming explicitly, since "what's compensated" (errors doc, E1/E2) depends on which one a failure occurs in:

| Sub-state | Meaning |
|---|---|
| **Started** | Booking record created; no reservation calls dispatched yet. |
| **Reservations pending** | The three reservation calls are in flight; some may have already succeeded while others are still outstanding. |
| **Payment held** | All three reservations succeeded; funds are held, confirmations not yet dispatched. |
| **Confirmations pending** | The three confirmation calls are in flight. |
| **Compensating** | The orchestrator is unwinding whichever steps already succeeded, tracking which compensations are still outstanding — triggered either by a step failure (error scenarios E1/E2) or by an explicit cancellation request (see [controller layer plan](./controller-layer-plan.md)). |

`Compensating` resolves into `Failed` when triggered by a step failure, or into `Cancelled` when triggered by an explicit cancellation request, once all outstanding compensations are acknowledged.
