# Use Case: Complete a Travel Booking — Error & Compensation Flows

> Failure scenarios for the [main success scenario](./use-case-main.md), and the business-level compensation each one requires. Terms defined in [`business-definitions.md`](./business-definitions.md). Status: **PENDING terminology reconciliation — not yet reviewed against the main case's current wording (e.g. "offer reference" vs. the earlier "business key" phrasing) or its 8-step numbering; the E1/E2 content and step citations predate that rename. E1/E2/E3 are implemented (`TravelBookingSagaWorkflowImpl`); E4 (refund) remains an open business-logic question — see §5.**

## Why compensation splits into exactly two shapes

The [main flow](./use-case-main.md)'s payment hold (step 4) can only be reached once **all three** reservations have already succeeded (step 3). That precondition is what separates the error scenarios below into two clean groups rather than one general "undo whatever succeeded" mechanism:

- **Before the hold (E1, E2):** at most the three reservations have happened — payment was never touched. Compensation is reservation-cancellation only.
- **After the hold (E3, E4):** all three reservations are guaranteed to have succeeded, and money has moved. Compensation now involves payment (hold-release or refund), not just reservations.

## E1 — A reservation fails before payment is held

**Trigger:** one of the three parallel reservation calls (step 2 of the main flow) fails — e.g. car has no availability, or the business key's 10-minute window (definitions, §4) has expired by the time the call reaches the provider — while the other one or two succeed, or are still in flight.

**Flow:**
1. The orchestrator observes the failure and inspects which of the three reservations actually succeeded. (Open sub-question: if one of the other two legs hasn't responded *at all* yet when the failure arrives — not failed, just still pending — is that a timeout or does the orchestrator wait for it? See open questions below.)
2. It cancels **only the legs that succeeded**, dispatched in parallel — no ordering between them is required, since the legs are independent.
3. Once all necessary cancellations are acknowledged, the booking is marked **Failed** with the failure reason.

**Outcome:** booking **Failed**. No money was ever held or charged — compensation is limited to releasing whichever reservations were made.

## E2 — The payment hold itself fails

**Trigger:** step 4 of the main flow (hold payment) fails, after step 3 has already confirmed all three reservations succeeded.

**Flow:**
1. Because the hold is only attempted after all three reservations are confirmed, compensation here is **deterministic, not partial** — all three legs need cancelling, not "whichever ones happen to have succeeded."
2. The orchestrator cancels all three reservations, in parallel.
3. Booking is marked **Failed**.

**Outcome:** booking **Failed**. No refund needed — nothing was ever captured, and the hold itself never took effect.

## E3 — A confirmation fails after the hold *(resolved)*

**Trigger:** one of the three parallel confirmation calls (step 5 of the main flow) fails, after payment has already been held (step 4).

**Flow:**
1. The orchestrator releases the payment hold (`releasePaymentHold`) — a **hold release**, not a refund, since capture (step 7) hasn't happened. The amount is whatever `paymentAuthorizationToken` authorized (main use case, Preconditions); nothing is recomputed.
2. It does **not** touch any leg reservation. A leg that already reached `Confirmed` cannot be un-confirmed — `Confirmed` is terminal (definitions §3), and the leg services' cancel endpoint rejects a confirmed reservation (409). A leg that was only `Reserved` when the failure hit is left `Reserved`.
3. Booking is marked **Failed** with the failure reason.

**Outcome:** booking **Failed**. Payment hold released, no capture, no refund. Legs end in a mix of `Confirmed` and `Reserved` — an accepted Failed-booking end state, since there is no mechanism (and no business need) to force them all back to a single status.

**Decisions made** (the questions this scenario carried while open):
- *Does a confirmation failure unwind that leg's reservation?* **No** — a `Reserved`-not-`Confirmed` leg is a valid Failed end state; cancelling only that leg while the others are irrevocably `Confirmed` produces a *more* inconsistent booking, not less.
- *Retry the failed confirmation first?* Temporal's Activity retry already redelivers transient failures transparently. E3 compensation fires only once the confirmation Activity gives up (a non-retryable failure) — symmetric with E1/E2. Per-step retry *tuning* stays open (question #4).
- *Unwind the other, possibly-already-`Confirmed` legs?* **No, and it isn't possible** — see step 2. The compensation owed, because money moved, is the hold release.

## E4 — Payment capture fails, or a failure is discovered after capture *(open)*

**Trigger:** step 7 of the main flow (capture payment) fails outright, or a failure in booking completion is discovered after capture already succeeded.

**Status: unresolved**, and qualitatively different from E1–E3. The hold-vs-capture distinction drives why:

| Payment state at failure | Compensation | Characteristics |
|---|---|---|
| **Held** (not yet captured) — E1/E2/E3 | Release the hold | Reversible, typically instant, no real charge to reverse |
| **Captured** — E4 | Real refund against the payment gateway | Asynchronous, can fail independently, may take days to settle — a true compensating transaction |

What's still undecided:
- Whether refund compensation runs inside the same saga/workflow execution, or is delegated to a separate, longer-running refund-tracking process that the saga merely triggers and doesn't wait on.
- What "Failed" means for a booking where the legs are all genuinely confirmed but the refund is still pending — is that a distinct status from E1/E2/E3's Failed?

## Summary table

| Scenario | Trigger point | What's compensated | Refund needed? | Status |
|---|---|---|---|---|
| E1 | A reservation fails (pre-hold) | Whichever reservations succeeded | No | Resolved |
| E2 | The hold fails | All three reservations (guaranteed succeeded) | No | Resolved |
| E3 | A confirmation fails (post-hold) | Hold release only — leg reservations left as-is | No (hold only) | Resolved |
| E4 | Capture fails / post-capture failure | Real refund | Yes | **Open** |

## Open questions carried forward

1. ~~**E3 resolution**~~ — resolved and implemented (see E3 above): hold release only, leg reservations left as-is.
2. **E4 resolution** — see above, including whether it belongs in this saga at all.
3. **Idempotency of compensation operations** — cancel/release/refund endpoints need the same `sagaId`-keyed idempotency discipline as the forward-path operations (definitions §3), since compensation commands may be retried or redelivered.
4. **Timeout/retry policy per step** — reservation vs. confirmation vs. hold vs. capture may each warrant a different policy; not yet specified for any of them.
5. **Timeout vs. explicit failure during E1** — if one leg fails while another hasn't responded at all yet (as opposed to having explicitly failed), does the orchestrator treat the non-responder as failed after some timeout, or wait indefinitely for it to settle before compensating? Not yet decided.
