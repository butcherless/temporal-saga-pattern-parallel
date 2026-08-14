# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A sibling to **`temporal-saga-pattern`**, holding the orchestration engine and tech stack constant
(same Temporal approach, same Spring Boot/R2DBC/Flyway/OpenAPI stack, same module-layout style) while
varying the **shape of the business dependency graph**: instead of a dependent chain (Order → Inventory
→ Payment), this repo coordinates three genuinely independent reservations (flight, hotel, car) fanned
out in parallel, gated by a single payment hold/capture step in between. See that repo's own `CLAUDE.md`
and `docs/temporal-saga-parallel-plan.md` for the original comparison rationale.

The business case, definitions, and use-case flows for this repo live in
**[`docs/travel-booking/`](./docs/travel-booking/)** — read those before extending this repo; this
file only covers what you need to operate/build it day to day, and what's implemented vs. not.

- [`business-definitions.md`](./docs/travel-booking/business-definitions.md) — canonical vocabulary (actors, identifiers, money/pricing, status lifecycle).
- [`use-case-main.md`](./docs/travel-booking/use-case-main.md) — the main success scenario, step by step.
- [`use-case-alternatives.md`](./docs/travel-booking/use-case-alternatives.md) — non-error flows. **PENDING**, not yet reconciled with the current terminology/step numbering.
- [`use-case-errors.md`](./docs/travel-booking/use-case-errors.md) — failure/compensation scenarios E1-E4. **PENDING** on the same grounds; E1/E2 are resolved regardless (implemented below), E3/E4 are open regardless of the reconciliation.
- [`controller-layer-plan.md`](./docs/travel-booking/controller-layer-plan.md) — the REST contract for `booking-service`'s two client-facing operations.

## Current state — what's real vs. scaffolded

This repo was scaffolded in one pass from `temporal-saga-pattern`'s structure/tech stack/code style,
per that repo's own precedent of "fix the structure well enough to be mechanical, then refine as the
project progresses." Concretely, as of this scaffold:

**Implemented:**
- `saga-common` — fully implemented: `error/` (carried over unchanged from `temporal-saga-pattern`,
  same precedent as that repo's own relationship to `saga-pattern-poc`), `workflow/`
  (`TravelBookingSagaInput` + per-leg records, `TravelBookingSagaWorkflow`, `SagaTaskQueues`).
- `booking-service` — fully implemented per `docs/travel-booking/controller-layer-plan.md`: `POST
  /travel-bookings` (buy, starts the saga Workflow) and `POST /travel-bookings/{sagaId}/cancel`
  (signals it), plus a third, internal-only `POST /travel-bookings/{sagaId}/confirm` (called by the
  orchestrator's `BookingActivities`, added here because the happy path needs it — not part of the
  controller-layer plan's original 2-operation scope, worth folding back into that doc later).
- `saga-orchestrator-temporal`'s `TravelBookingSagaWorkflowImpl` — the happy path (use-case-main.md
  steps 1-8) is fully implemented, including E1/E2 compensation (use-case-errors.md) via manual
  per-`Promise` inspection rather than `io.temporal.workflow.Saga` (that class's LIFO stack doesn't
  fit a parallel fan-out — see that Workflow class's own javadoc).
- `flight-service`, `hotel-service`, `car-service` — `domain`/`persistence`/`service` layers
  implemented: a `{Flight,Hotel,Car}Reservation` aggregate (record) + `ReservationStatus`
  (`RESERVED`→`{CONFIRMED, CANCELLED}`, both terminal) + `IllegalReservationTransitionException`,
  a bare `ReactiveCrudRepository`, and a `*ReservationProgressionService` with
  reserve/confirm/cancel — idempotent by `sagaId`, same shape as `booking-service`'s
  `TravelBookingProgressionService`. Each has a V1 Flyway migration. Unit-tested against a mocked
  repository (Mockito + `StepVerifier`) — 10 tests per service, covering idempotency, the
  not-found path, and both illegal-transition cases (confirming a cancelled reservation, cancelling
  a confirmed one).
- `payment-service` — same shape, adapted to hold/capture/release: `PaymentHold` aggregate +
  `PaymentHoldStatus` (`HELD`→`{CAPTURED, RELEASED}`, both terminal — capturing then releasing is
  correctly rejected, since undoing a capture is a refund, not a release, per
  business-definitions.md §3) + `IllegalPaymentHoldTransitionException`,
  `PaymentHoldRepository`, `PaymentHoldProgressionService`. No amount field anywhere — same "no
  price computation" rule as everywhere else in this saga.
- No web/REST layer yet on any of the four business services (see below).

**Scaffolded, not yet implemented:**
- `flight-service`, `hotel-service`, `car-service`, `payment-service` — no `web` layer. Their REST
  contracts (reserve/confirm, hold/capture) haven't been designed yet the way `booking-service`'s
  was in `controller-layer-plan.md`; that's the natural next doc to write, and is why
  `saga-orchestrator-temporal`'s Activity implementations (below) still can't exist either.
- `saga-orchestrator-temporal`'s Activity **implementations** (the `FlightActivities`/
  `HotelActivities`/`CarActivities`/`PaymentActivities` interfaces exist and are used by
  `TravelBookingSagaWorkflowImpl`; their `WebClient`-based `*Impl` classes don't exist yet, since
  they'd call REST endpoints that don't exist yet on the four services above). No
  `spring.temporal.workers` block in that module's `application.yml` either, for the same reason —
  there's nothing to register as an Activity bean yet.
- `TravelBookingSagaWorkflow#cancel()` — the signal exists and `booking-service` sends it, but
  `TravelBookingSagaWorkflowImpl` receives it into an unread field; nothing compensates an in-flight
  saga on cancellation yet. Depends on E1/E2/E3 all being resolved with a consistent mechanism first.
- `platform-test` — pom.xml only, no `*IT.java`. Can't drive a real end-to-end saga until the four
  business services have working REST endpoints.
- `booking-service`'s own `service`/`domain` layers still have no dedicated unit tests of their own
  (only `BookingControllerTest`, which mocks the service) — a pre-existing gap from the initial
  scaffold, not touched by the leg/payment service work above.

## Conventions

Same conventions as `temporal-saga-pattern` (and, transitively, `saga-pattern-poc`): English-only
source/docs, TDD, Lombok-only logging, reactive stack wherever a reactive Spring starter exists,
`final` method arguments via the ad hoc OpenRewrite invocation below, no primitive types in public
signatures, OpenAPI 3.1 code-first REST endpoints, append-only Flyway migrations, idempotency-by-
`sagaId`/`businessKey` discipline per service. Not repeated here — see that repo's own `CLAUDE.md`
for the full rationale behind each.

### Logging

`temporal-saga-pattern`'s own `CLAUDE.md` only states "Lombok-only logging" (`@Slf4j`, never a
hand-declared `Logger` field) — everything else about its logging is an unwritten pattern, not a
documented rule, even there (confirmed by inspecting its actual source: no javadoc, no `docs/`
entry states it). This project codifies that pattern explicitly instead of leaving it to be
re-derived by reading code:

Every public method with real implementation code in the `service`, `web`, and `persistence`
layers must open with a `log.debug(...)` call as its first statement — before any other logic —
naming the method and its input, via SLF4J's `{}` placeholders (never string concatenation or
manual `+`). One parameter: `log.debug("methodName - {}", param)`. Multiple parameters: name each,
`log.debug("methodName - param1={}, param2={}", param1, param2)`. "`persistence` with implementation
code" excludes bare Spring Data repository interfaces with no method bodies (there's nothing to log
in a declaration) — it only applies once a repository/DAO gets a hand-written method.

Not logged, matching the reference project's own actual (if unwritten) practice: `domain` classes,
persistence interfaces with no implementation, `config` classes, and request/response DTOs.
`Application` classes are one exception, each logging a single `log.info("<service-name> started")`
on startup.

The Temporal Workflow implementation (`TravelBookingSagaWorkflowImpl`) is logged too, following the
same "first statement, `DEBUG`, method name + input, `{}` placeholders" rule — but it's the other
exception to "Lombok-only logging," and deliberately so: a plain `@Slf4j` logger would re-emit every
line on every deterministic replay (Workflow methods re-execute from the top as Temporal replays
history). It must instead declare `private static final Logger log =
Workflow.getLogger(TravelBookingSagaWorkflowImpl.class)` — `io.temporal.workflow.Workflow`'s own
replay-aware logger, which suppresses the replay-time duplicates and only actually logs on the real
execution. Activity implementations (`saga-orchestrator-temporal`'s `activities/*ActivitiesImpl`,
once they exist) are plain Spring beans making blocking HTTP calls, not Workflow code subject to
replay — they use ordinary `@Slf4j`, same as `service`/`web`/`persistence`.

### String null-or-blank checks

When a single boolean decision is "is this string absent" (null or blank collapsed into one
question — e.g. an optional field defaulting to something else when unset), use
`!org.springframework.util.StringUtils.hasText(value)` in one call, never a hand-rolled
`value == null || value.isBlank()`. Example: `CreateBookingRequestBody.hasNoBusinessKey()`.

This is a **different case** from a compact constructor's required-field validation (every
`saga-common` and `booking-service` record's canonical constructor): those deliberately keep
`Objects.requireNonNull(value, "value must not be null")` and a separate `if (value.isBlank())
throw new IllegalArgumentException("value must not be blank")` as two steps, not one
`StringUtils.hasText` check — collapsing them would lose the distinct exception type and message
per failure mode (a null field surfaces as `NullPointerException`/"must not be null", a
present-but-blank one as `IllegalArgumentException`/"must not be blank"), which `RestExceptionHandler`
and the tests both rely on. `StringUtils.hasText` is for a single yes/no presence question with one
outcome, not for input validation that needs to say *which* way the field was invalid.

## Architecture

### What's identical to `temporal-saga-pattern`

Maven multi-module layout style, Spring Boot reactive stack, `temporal-spring-boot-starter`,
`docker-compose.yml` infra pattern (Postgres + `temporalio/server` + one-shot
`temporalio/admin-tools` schema setup + Temporal Web UI), the ad hoc OpenRewrite tooling
(`tooling/openrewrite-one-param-per-line`, copied verbatim — fully generic, not order- or
booking-specific). Host port *numbers* are deliberately not identical — see Local infra, below.

### Module layout

- `saga-common` — Spring-free: `error/` + `workflow/` (`TravelBookingSagaWorkflow`,
  `TravelBookingSagaInput` + `FlightBookingRequest`/`HotelBookingRequest`/`CarBookingRequest`,
  `SagaTaskQueues`).
- `booking-service` — `domain`/`persistence`/`service`/`web`; starts and signals the Workflow, and
  is called back by it to confirm.
- `flight-service`, `hotel-service`, `car-service`, `payment-service` — `domain`/`persistence`/
  `service` implemented; no `web` yet (see above).
- `saga-orchestrator-temporal` — `workflow/TravelBookingSagaWorkflowImpl` (implemented) +
  `activities/` (five `@ActivityInterface`s, implemented; `*Impl` classes not yet) +
  `config/` (not yet — `ActivityWebClientConfig`/`VirtualThreadWorkerOptionsCustomizer` equivalents
  wait on the leg/payment services' `web` layers existing).
- `platform-test` — pom.xml only for now.

Ports are offset by +10 from `temporal-saga-pattern`'s equivalent range (`8080`-`8083`), same
"can't collide if both projects' services ever run side by side on bare `localhost`" reasoning as
the Local infra port offsets below.

| Service | Port | DB |
|---|---|---|
| `saga-orchestrator-temporal` | 8090 | — |
| `booking-service` | 8091 | `booking_db` |
| `flight-service` | 8092 | `flight_db` |
| `hotel-service` | 8093 | `hotel_db` |
| `car-service` | 8094 | `car_db` |
| `payment-service` | 8095 | `payment_db` |

## Commands

Same Java 25 toolchain (`.sdkmanrc`), `./mvnw` wrapper, and static-analysis commands as
`temporal-saga-pattern` — see that repo's `CLAUDE.md` for the exact invocations (`final`-convention
OpenRewrite recipe, the one-param-per-line custom recipe module). `./mvnw clean verify` doesn't need
Docker yet — there's no Testcontainers-backed IT until `platform-test` gets one.

### Local infra (PostgreSQL + Temporal)

```bash
docker compose up -d      # postgres:5433, temporal:7234 (gRPC), temporal-ui:8089, pgadmin:5051
docker compose down       # stop; add -v to also drop the postgres/pgadmin volumes
```

Every host-side port here is offset by one from `temporal-saga-pattern`'s equivalent
(`5432`→`5433`, `7233`→`7234`, `8088`→`8089`, `5050`→`5051`) — container-internal ports are
unchanged, only the host mapping differs. Host ports aren't scoped per Docker Compose project the
way `container_name`/volumes are, so leaving them identical would make the two stacks impossible to
run side by side (`docker compose up` in the second one would fail with "port is already
allocated"). Same username (`saga`) as `temporal-saga-pattern`, but password `parallel` instead of
`saga`, and container-naming pattern renamed `saga-parallel-*` — same "can't be confused for each
other" reasoning. PostgreSQL hosts `booking_db`/`flight_db`/`hotel_db`/`car_db`/`payment_db`.

### Running `booking-service` locally

The only business service with anything to run right now:

```bash
./mvnw install -pl saga-common -DskipTests -q
./mvnw spring-boot:run -pl booking-service
```

Needs `TEMPORAL_TARGET` (defaults to `localhost:7234`, matching Local infra above) and the same `DB_*` env vars pattern as
`temporal-saga-pattern`'s services. `saga-orchestrator-temporal` can also be started
(`./mvnw spring-boot:run -pl saga-orchestrator-temporal`) and will connect, but has no Worker
registered yet (see Current state, above), so it won't pick up any Workflow Tasks — a booking created
via `booking-service` will start a Workflow Execution that then sits unprocessed until a Worker
exists to run it.

## Explicitly deferred

Everything listed under "Scaffolded, not yet implemented" above, plus (mirroring
`temporal-saga-pattern`'s own `CLAUDE.md` structure):

- A shared library between this repo and `temporal-saga-pattern` — same "not yet" as that repo's own
  relationship to `saga-pattern-poc`.
- Refund (`use-case-errors.md`, E4) and everything downstream of it.
- Status query (`GET /travel-bookings/{sagaId}`) — `use-case-alternatives.md` A2, not yet decided
  whether it's even in scope.
