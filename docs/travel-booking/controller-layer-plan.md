# Travel Booking — Controller Layer Plan

> REST contract for the operations a client can call directly: buy a vacation package, cancel one before it completes, and query its status. Builds on [`business-definitions.md`](./business-definitions.md) and [`use-case-main.md`](./use-case-main.md) — terms and identifiers from those docs (`sagaId`, `businessKey`, offer reference, payment authorization token, EUR) are used here without redefinition. Owning service: **`booking-service`**, the same aggregate-owner role established for the sibling `temporal-saga-pattern`/`temporal-saga-pattern-parallel` repos. Status: **implemented** in `booking-service`'s `web` package (`BookingController` plus a fourth internal operation — see Scope, below). All three endpoints exist and are tested; the one substantive gap is that Operation 2's `cancel` signal is not yet acted on by the orchestrator (Open items 1), so no booking currently reaches `CANCELLED`. Other open items (§ below) are refinements.

## Scope

Three client-facing operations:

| # | Operation | Method & path |
|---|---|---|
| 1 | Buy the vacation package | `POST /travel-bookings` |
| 2 | Cancel the buy | `POST /travel-bookings/{sagaId}/cancel` |
| 3 | Query booking status | `GET /travel-bookings/{sagaId}` |

Operation 3 was added after the fact per [alternatives](./use-case-alternatives.md) A2 — it was
originally out of scope for this plan's "two requested operations".

As built, `booking-service` also exposes a fourth, **internal-only** operation not part of this
plan's original scope: `POST /travel-bookings/{sagaId}/confirm`, called by the saga
Workflow's `BookingActivities` once payment is captured (main use case, step 8) — added because the
happy path has nothing else to call to reach `Confirmed`. Not documented in detail below since no
client ever calls it directly; see `BookingController` itself for its contract.

Explicitly out of scope for this plan (not modeled below):
- **Client-initiated refund.** Cancellation is only permitted before the booking reaches `Confirmed` (payment captured); cancelling an already-`Confirmed` booking is rejected (409). The saga *does* refund itself on an internal post-capture failure ([error scenarios](./use-case-errors.md) E4), but exposing a *client*-driven "cancel my confirmed booking → refund" flow is a separate customer-service concern, not modeled here. See §"Cancel operation" below.
- **Live per-leg progress in the status query.** Operation 3 reports only the coarse booking status, not which legs are reserved/confirmed so far — see [alternatives](./use-case-alternatives.md) A2 for why.
- Endpoints on the leg services (`flight-service`, `hotel-service`, `car-service`) or `payment-service` — every operation here only ever calls into `booking-service`, which drives the underlying saga; the client never calls a leg service directly.

## Conventions used below

- Status values are upper-snake-case strings: `PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED` (per [definitions §7](./business-definitions.md#7-booking-status--lifecycle)).
- Every error response is a standard **RFC 9457 `ProblemDetail`** — `booking-service`'s `RestExceptionHandler` (`web` package) extends Spring's `ResponseEntityExceptionHandler` and maps every domain exception through `ProblemDetail.forStatusAndDetail(...)`, the same convention `temporal-saga-pattern` already uses platform-wide, rather than a bespoke envelope invented for this plan:
  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "No booking found for sagaId 6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f"
  }
  ```
  `detail` carries the domain exception's own message. There is **no machine-readable error code and no dedicated `sagaId` field** — a caller distinguishing failure cases programmatically reads the HTTP status code (409 vs. 404 vs. 400 below) and, for `sagaId`, the request path it already sent. See Open items #5 for the tradeoff this implies versus this plan's original (unbuilt) custom envelope.

---

## Operation 1 — Buy the vacation package

`POST /travel-bookings`

Implements [main use case](./use-case-main.md) step 1 (*Create booking*). Idempotent by `businessKey` (main use case, Idempotency table) — a duplicate submission returns the existing booking rather than starting a second saga.

### Request

Mirrors [definitions §6](./business-definitions.md#6-booking-request-fields) exactly: booking-level fields, plus the three legs, each carrying its own offer reference and no price.

| Field | Type | Required | Notes |
|---|---|---|---|
| `businessKey` | string | No | Client-supplied idempotency key. If omitted, the server generates one (same `hasNoBusinessKey()` pattern as the sibling repos). |
| `operatorId` | string | Yes | Business metadata only. |
| `paymentAuthorizationToken` | string | Yes | Opaque; see definitions §4. |
| `flight` | object | Yes | See fields below. |
| `hotel` | object | Yes | See fields below. |
| `car` | object | Yes | See fields below. |

`flight`: `flightNumber`, `originAirportCode`, `destinationAirportCode`, `departureDate` (ISO date), `passengerCount` (int), `offerReference` (UUID).
`hotel`: `hotelCode`, `roomType`, `checkInDate`, `checkOutDate`, `guestCount` (int), `offerReference` (UUID).
`car`: `carCategory`, `pickupLocation`, `pickupDate`, `dropoffDate`, `offerReference` (UUID).

No `price` field anywhere in this request — see definitions §5.

```json
{
  "businessKey": "biz-2026-08-13-0001",
  "operatorId": "agency-042",
  "paymentAuthorizationToken": "pat_9f3e2b7c8a1d4e5f",
  "flight": {
    "flightNumber": "IB6275",
    "originAirportCode": "MAD",
    "destinationAirportCode": "CDG",
    "departureDate": "2026-09-10",
    "passengerCount": 2,
    "offerReference": "b6e1a1d2-4b9e-4a3a-9c2e-6f2b8b1a9e10"
  },
  "hotel": {
    "hotelCode": "HTL-PARIS-014",
    "roomType": "DOUBLE_DELUXE",
    "checkInDate": "2026-09-10",
    "checkOutDate": "2026-09-14",
    "guestCount": 2,
    "offerReference": "c7f2b2e3-5c0f-4b4b-8d3f-7a3c9c2b0f21"
  },
  "car": {
    "carCategory": "COMPACT",
    "pickupLocation": "CDG",
    "pickupDate": "2026-09-10",
    "dropoffDate": "2026-09-14",
    "offerReference": "d8a3c3f4-6d1a-4c5c-9e4a-8b4d0d3c1f32"
  }
}
```

### Response — first submission

`202 Accepted` — the saga has started; still `PENDING`.

```json
{
  "sagaId": "6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f",
  "businessKey": "biz-2026-08-13-0001",
  "status": "PENDING"
}
```

### Response — idempotent replay

`200 OK` — a booking for this `businessKey` already exists; no new saga started. Body is the same shape, reflecting whatever the existing booking's current status actually is (`PENDING`, `CONFIRMED`, `CANCELLED`, or `FAILED`) — not necessarily `PENDING` again.

```json
{
  "sagaId": "6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f",
  "businessKey": "biz-2026-08-13-0001",
  "status": "CONFIRMED"
}
```

### Error responses

`400 Bad Request` — structural validation only (missing/malformed field); no business-rule validation happens synchronously here, since offer/price validity is only checked when each leg service processes its reservation call (main use case, step 2). `detail` is whichever leg record's own compact-constructor validation failed first — it names the field, not its parent object (there's no `flight.` prefix; `FlightLegRequestBody` doesn't know it's nested).

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "offerReference must not be null"
}
```

---

## Operation 2 — Cancel the buy

`POST /travel-bookings/{sagaId}/cancel`

New operation, not previously modeled in the use-case docs. **Business rule: only permitted while the booking has not yet reached `Confirmed`.** Once `Confirmed`, payment has been captured (main use case, step 7) and undoing it needs a client-facing refund flow that isn't modeled here (see Scope, above) — so this endpoint rejects the request instead of attempting one. (The saga's own internal E4 refund is a different path — see use-case-errors.md.)

While still `Pending` (any sub-state — see [definitions §7](./business-definitions.md#7-booking-status--lifecycle)), the intent is for cancelling to drive the same compensation mechanics as a step failure: whichever reservations already succeeded are cancelled, and the payment hold is released if one exists. **Reservation-only compensation (booking never reached `Payment held`) would reuse [error scenario E1](./use-case-errors.md)'s mechanics; hold-release (booking reached `Payment held` or `Confirmations pending`) would reuse E2/E3's.** The booking's terminal status would then become `Cancelled`, not `Failed` (definitions §7) — same mechanics, different trigger, kept distinguishable for support/reporting.

**Not yet wired (see [Open items](#open-items) 1).** The endpoint is built — it validates the booking hasn't reached `Confirmed`/`Failed` and sends the `cancel` signal to the Workflow — but `TravelBookingSagaWorkflowImpl` receives that signal into an unread field: no compensation runs on cancellation, and no booking ever actually reaches `CANCELLED`. The E1/E2/E3/E4 compensation mechanics exist only on the step-failure paths so far. The rest of this section describes the target contract.

Idempotent by `sagaId`: a repeat cancel call on a booking that's already `Cancelled` (or already compensating toward it) returns the current state rather than re-triggering compensation.

### Request

No required body. Optional free-text reason, for audit only — not used in any business logic:

```json
{
  "reason": "customer changed mind"
}
```

### Response — cancellation accepted

`202 Accepted` — booking was `Pending`; the `cancel` signal has been sent. (Target: compensation
has started. Currently the orchestrator does not act on the signal — see the "Not yet wired" note
above.)

```json
{
  "sagaId": "6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f",
  "status": "PENDING"
}
```

(Status is still reported as `PENDING` here, not a new value — internally the booking would be in the `Compensating` sub-state, but that sub-state isn't exposed through this endpoint's response any more than `Reservations pending` or `Payment held` are; see Scope, above, on why a status-query endpoint is out of scope for this plan. In the target design the booking reaches `CANCELLED` asynchronously; today it does not, since the orchestrator ignores the signal.)

### Response — idempotent replay (already cancelled)

`200 OK` — no new compensation triggered.

```json
{
  "sagaId": "6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f",
  "status": "CANCELLED"
}
```

### Error responses

`409 Conflict` — booking already `Confirmed`; cancellation refused because it would require a refund.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking 6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f is already confirmed and paid; cancellation after confirmation requires a refund, which is not yet supported"
}
```

`404 Not Found` — no booking exists for this `sagaId`.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No booking found for sagaId 6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f"
}
```

`409 Conflict` — booking already `Failed` (ended via a step failure, not a cancellation). Cancelling a booking that already failed for an unrelated reason is treated as a conflict rather than a silent no-op, since the two terminal states have different meanings (definitions §7) and collapsing them would hide *why* the booking ended:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking 6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f already ended due to a step failure, not a cancellation request"
}
```

---

## Operation 3 — Query booking status

`GET /travel-bookings/{sagaId}`

Implements [alternatives](./use-case-alternatives.md) A2. Read-only: returns the `travel_booking`
row's current status without touching the running saga. Reports the **coarse booking status only**
(`PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED`), not per-leg progress — same deliberate scoping as
A2 (no Workflow `@QueryMethod`, no fan-out to the leg services).

### Request

No body. `sagaId` (also the booking's id) in the path.

### Response — booking found

`200 OK` — same `{sagaId, status}` shape as Operations 1 and 2.

```json
{
  "sagaId": "6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f",
  "status": "PENDING"
}
```

### Error responses

`404 Not Found` — no booking exists for this `sagaId`.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No booking found for sagaId 6a9c9e2a-1b3d-4c9a-8b7e-2f1a9c3d5e6f"
}
```

---

## Open items

1. **Cancel → compensation not wired.** E1/E2/E3/E4 compensation is implemented in `TravelBookingSagaWorkflowImpl` on the *step-failure* paths (e.g. E3: a confirmation fails after the hold → release the payment hold, leave leg reservations as-is, fail the booking — see [errors doc](./use-case-errors.md) E3). Operation 2's cancel signal is *not* yet hooked into any of them: `TravelBookingSagaWorkflowImpl.cancel()` sets an unread flag, so a cancelled saga runs to its normal end and `booking-service`'s `CANCELLED` branch is unreachable. Closing this means checking the flag at the fan-out boundaries and driving the matching compensation path.
2. **Client-initiated refund.** A client-facing "refund my confirmed booking" operation is still not modeled (Scope, above). The saga's own E4 refund on an internal post-capture failure is implemented (`TravelBookingSagaWorkflowImpl`, use-case-errors.md E4) — that's saga compensation, not a client operation.
3. **Status query granularity.** Operation 3 (`GET /travel-bookings/{sagaId}`) is built but reports only the coarse booking status. Surfacing live per-leg progress ("flight reserved, hotel reserved, car pending") would need a Temporal `@QueryMethod` on the Workflow plus the Workflow tracking that sub-state — deferred (see [alternatives](./use-case-alternatives.md) A2), not currently a product requirement.
4. **`FAILED` vs. `CANCELLED` conflict handling on cancel.** Treating a cancel-after-`Failed` as `409` rather than an idempotent `200` is a judgment call made in this plan and now built as specified (`BookingAlreadyFailedException` → 409) — still worth confirming, since it's not dictated by any existing doc.
5. **`ProblemDetail` drops `sagaId` and the machine-readable error code this plan originally specified.** Adopting Spring's standard `ProblemDetail` (Conventions, above) for platform consistency means every error response lost two things a caller might reasonably want: a stable code to switch on without parsing `detail` text, and `sagaId` echoed back in the body rather than only known from the request path. Both are addable as `ProblemDetail` extension properties (`setProperty("sagaId", ...)`, `setProperty("errorCode", ...)`) without abandoning RFC 9457 — not done yet, since none of `booking-service`'s current domain exceptions expose `sagaId` via a getter for a shared handler to read.
