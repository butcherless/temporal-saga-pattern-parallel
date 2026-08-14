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
project progresses." The main use case (use-case-main.md, steps 1-8) is now fully implemented and
verified end-to-end — every layer of every service reads and writes real data in its own Postgres
database, proven by `platform-test`'s `TravelBookingEndToEndSagaIT` (see below).

**Implemented:**
- `saga-common` — fully implemented: `error/` (carried over unchanged from `temporal-saga-pattern`,
  same precedent as that repo's own relationship to `saga-pattern-poc`), `workflow/`
  (`TravelBookingSagaInput` + per-leg records, `TravelBookingSagaWorkflow`, `SagaTaskQueues`).
- `booking-service` — fully implemented per `docs/travel-booking/controller-layer-plan.md`: `POST
  /travel-bookings` (buy, starts the saga Workflow) and `POST /travel-bookings/{sagaId}/cancel`
  (signals it), plus a third, internal-only `POST /travel-bookings/{sagaId}/confirm` (called by the
  orchestrator's `BookingActivities`, added here because the happy path needs it — outside that
  doc's original 2-operation scope, worth folding back into it later).
- `flight-service`, `hotel-service`, `car-service` — `domain`/`persistence`/`service` layers: a
  `{Flight,Hotel,Car}Reservation` aggregate (record) + `ReservationStatus`
  (`RESERVED`→`{CONFIRMED, CANCELLED}`, both terminal) + `IllegalReservationTransitionException`,
  a bare `ReactiveCrudRepository`, and a `*ReservationProgressionService` with
  reserve/confirm/cancel — idempotent by `sagaId`, same shape as `booking-service`'s
  `TravelBookingProgressionService`. Each has a V1 Flyway migration. Unit-tested against a mocked
  repository (Mockito + `StepVerifier`) — 10 tests per service, covering idempotency, the
  not-found path, and both illegal-transition cases (confirming a cancelled reservation, cancelling
  a confirmed one). Each now also has a `web` layer: `*Controller` (`POST /{leg}-reservations`,
  `/{sagaId}/confirm`, `/{sagaId}/cancel`), `Reserve*RequestBody`/`*ReservationResponseBody`,
  `*WebConfig`, and a `RestExceptionHandler` mapping `IllegalStateException` (the not-found signal
  `loadReservation` already threw — pinned by the existing service-layer tests, not a new type) to
  404 and `IllegalReservationTransitionException` to 409. Their REST contracts weren't written up as
  a standalone plan doc the way `booking-service`'s was (`controller-layer-plan.md`); they're
  self-documented via OpenAPI annotations and each controller's own javadoc instead — worth writing
  up formally later if these contracts grow past what the code says on its own. Unit-tested via
  `WebTestClient` bound to the controller with a mocked progression service, same pattern as
  `booking-service`'s own `BookingControllerTest`.
- `payment-service` — same shape, adapted to hold/capture/release: `PaymentHold` aggregate +
  `PaymentHoldStatus` (`HELD`→`{CAPTURED, RELEASED}`, both terminal — capturing then releasing is
  correctly rejected, since undoing a capture is a refund, not a release, per
  business-definitions.md §3) + `IllegalPaymentHoldTransitionException`,
  `PaymentHoldRepository`, `PaymentHoldProgressionService`. No amount field anywhere — same "no
  price computation" rule as everywhere else in this saga. `web` layer: `PaymentController` (`POST
  /payment-holds`, `/{sagaId}/capture`, `/{sagaId}/release`), same DTO/config/exception-handler
  shape as the three reservation services above (`IllegalPaymentHoldTransitionException` → 409).
- `saga-orchestrator-temporal` — `TravelBookingSagaWorkflowImpl` implements the happy path
  (use-case-main.md steps 1-8), including E1/E2 compensation (use-case-errors.md) via manual
  per-`Promise` inspection rather than `io.temporal.workflow.Saga` (that class's LIFO stack doesn't
  fit a parallel fan-out — see that Workflow class's own javadoc). Its five Activity
  **implementations** (`FlightActivitiesImpl`/`HotelActivitiesImpl`/`CarActivitiesImpl`/
  `PaymentActivitiesImpl`/`BookingActivitiesImpl`) are plain `@Slf4j`/`@Component` beans, each
  making one blocking `WebClient` call per method into the matching business service's `web` layer
  above — no retry/non-retryable-failure classification yet (see Open item below). `config/`
  (`ServicesProperties`, `ActivityWebClientConfig` — one named `WebClient` bean per business
  service — and `VirtualThreadWorkerOptionsCustomizer`) mirrors `temporal-saga-pattern`'s own
  classes of these names. `application.yml`'s `spring.temporal.workers` block registers
  `TravelBookingSagaWorkflowImpl` and all five Activity beans on `travel-booking-saga-task-queue`.
  Unit-tested: the Workflow via `TestWorkflowEnvironment`/`FakeActivities` (no real Temporal Server,
  no HTTP), and each `*ActivitiesImpl` via a Mockito-stubbed `ExchangeFunction`
  (`ExchangeFunctionStub`, asserting the method/path posted to) — same two-tier pattern as
  `temporal-saga-pattern`'s own orchestrator tests.
- `platform-test` — `TravelBookingEndToEndSagaIT`: boots all six services as real forked `java -jar`
  exec-jar processes against a Testcontainers Postgres (all five databases, `max_connections=300` —
  same fix as Local infra's `docker-compose.yml` below, needed for the same reason) and a
  `temporalio/temporal` dev-server container, drives one full saga over real HTTP via `POST
  /travel-bookings`, and asserts both the Workflow Execution's own outcome and the row each service
  actually persisted in its own database (`travel_booking.status = CONFIRMED`, all three
  `*_reservation.status = CONFIRMED`, `payment_hold.status = CAPTURED`) — plus one
  idempotent-duplicate-`businessKey` scenario. Same structure as `temporal-saga-pattern`'s own
  `TemporalEndToEndSagaIT`.

**Open items (not blockers, but worth revisiting):**
- `TravelBookingSagaWorkflow#cancel()` — the signal exists and `booking-service` sends it, but
  `TravelBookingSagaWorkflowImpl` receives it into an unread field; nothing compensates an in-flight
  saga on cancellation yet. Depends on E1/E2/E3 all being resolved with a consistent mechanism first.
- No retry/non-retryable-failure classification in the five `*ActivitiesImpl` classes yet (unlike
  `temporal-saga-pattern`'s own, which map a 422 business rejection to a non-retryable
  `ApplicationFailure`) — this repo's leg/payment services signal illegal transitions as 409, not
  422, and per-step timeout/retry policy is still an open item in use-case-errors.md; revisit once
  that's resolved.
- `booking-service`'s `TravelBookingProgressionService` (`service` layer) is unit-tested
  (`TravelBookingProgressionServiceTest`, mocked `TravelBookingRepository`) covering `createBooking`/
  `confirmBooking` (main-case steps 1 and 8), but — like every service in this repo — has no
  dedicated `domain`-layer unit tests (`TravelBooking`, `{Flight,Hotel,Car}Reservation`,
  `PaymentHold`, and their `*Status` enums); each is exercised only indirectly through its
  service-layer test. A deliberate, consistent choice given how thin the transition-graph logic is,
  not an oversight; revisit if that logic grows less trivial.

## Conventions

Same conventions as `temporal-saga-pattern` (and, transitively, `saga-pattern-poc`): English-only
source/docs, TDD, Lombok-only logging, reactive stack wherever a reactive Spring starter exists,
`final` method arguments via the ad hoc OpenRewrite invocation below, no primitive types in public
signatures, OpenAPI 3.1 code-first REST endpoints, append-only Flyway migrations, idempotency-by-
`sagaId`/`businessKey` discipline per service. Not repeated here — see that repo's own `CLAUDE.md`
for the full rationale behind each.

### Logging

`temporal-saga-pattern`'s own `CLAUDE.md` only states "Lombok-only logging" (`@Slf4j`, never a
hand-declared `Logger` field) — everything else about its logging is an unwritten pattern, confirmed
by inspecting its source (no javadoc, no `docs/` entry states it). This project codifies that
pattern explicitly instead of leaving it to be re-derived by reading code:

Every public method with real implementation code in the `service`, `web`, and `persistence`
layers must open with a `log.debug(...)` call as its first statement, naming the method and its
input via SLF4J's `{}` placeholders (never string concatenation or manual `+`). One parameter:
`log.debug("methodName - {}", param)`. Multiple parameters: name each,
`log.debug("methodName - param1={}, param2={}", param1, param2)`. "`persistence` with implementation
code" excludes bare Spring Data repository interfaces with no method bodies (there's nothing to log
in a declaration) — it only applies once a repository/DAO gets a hand-written method.

Not logged, matching the reference project's own actual (if unwritten) practice: `domain` classes,
persistence interfaces with no implementation, `config` classes, and request/response DTOs.
`Application` classes are one exception, each logging a single `log.info("<service-name> started")`
on startup.

The Temporal Workflow implementation (`TravelBookingSagaWorkflowImpl`) is logged too, following the
same "first statement, `DEBUG`, method name + input, `{}` placeholders" rule — but it's the other
exception to "Lombok-only logging," deliberately: a plain `@Slf4j` logger would re-emit every
line on every deterministic replay (Workflow methods re-execute from the top as Temporal replays
history). It must instead declare `private static final Logger log =
Workflow.getLogger(TravelBookingSagaWorkflowImpl.class)` — `io.temporal.workflow.Workflow`'s own
replay-aware logger, which suppresses the replay-time duplicates and only actually logs on the real
execution. Activity implementations (`saga-orchestrator-temporal`'s `activities/*ActivitiesImpl`)
are plain Spring beans making blocking HTTP calls, not Workflow code subject to replay — they use
ordinary `@Slf4j`, same as `service`/`web`/`persistence`.

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
  `service`/`web`, same layout as `booking-service` (see Current state above).
- `saga-orchestrator-temporal` — `workflow/TravelBookingSagaWorkflowImpl`, `activities/` (five
  `@ActivityInterface`s plus their `WebClient`-based `*Impl` classes), `config/`
  (`ServicesProperties`/`ActivityWebClientConfig`/`VirtualThreadWorkerOptionsCustomizer`) — see
  Current state above.
- `platform-test` — `TravelBookingEndToEndSagaIT` (see Current state above).

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
OpenRewrite recipe, `org.openrewrite.staticanalysis.ExplicitThis` for explicit `this.` qualification,
the one-param-per-line custom recipe module). `./mvnw test` (all modules' unit tests) doesn't need
Docker. `./mvnw verify -pl platform-test` does — it exercises `TravelBookingEndToEndSagaIT`
(Testcontainers Postgres + a Temporal dev-server container), and needs the six service modules'
exec jars already built (`./mvnw verify -pl booking-service,flight-service,hotel-service,car-service,payment-service,saga-orchestrator-temporal -am`
first, or a plain `./mvnw verify` from the root covering the whole reactor).

**Building the runnable `-exec.jar` artifact** (e.g. to `java -jar` a service manually, or after
editing a service's code/config and needing the change to actually take effect): always run
**`./mvnw clean verify -DskipTests -pl <module>[,<module>...] -am`** — both parts matter:

- **`verify`, not `package`.** The root `pom.xml`'s `spring-boot-maven-plugin` binds its
  `repackage` execution (with `classifier=exec`) to the `verify` phase, not `package` — this
  mirrors `temporal-saga-pattern`'s own root pom setup, and is called out in that plugin's own
  `<executions>` comment. Running `./mvnw package` alone **silently produces no `-exec.jar` at
  all** (only the plain, non-executable `<module>-<version>.jar` thin jar) — it doesn't fail or
  warn, so this is easy to miss until `java -jar ..-exec.jar` reports the file doesn't exist.
- **`clean`.** Without it, a stale `-exec.jar` from a previous build can survive in `target/`
  even after source/resource edits — confirmed firsthand: a config fix wasn't picked up by a
  `verify` run without `clean` first, and the jar's packaged `application.yml` still held the old
  content.

Plain `./mvnw test` needs neither — it recompiles what it needs on its own, and produces no jar.
This only matters when the goal is a jar meant to actually be run (manually, by `start-all.sh`, or
by an `e2e-*.sh` script — all of which already invoke `clean verify` internally, so only a manual,
single-module rebuild needs this spelled out).

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

### Running the full stack locally

All six services are runnable now, two ways:

**Scripted (preferred)** — `platform-test/scripts/start-all.sh` rebuilds all seven module jars,
recreates Postgres/Temporal from a clean slate (`docker compose down -v` first, so it never
inherits state from a previous run), starts the five business services then the Temporal Worker
last, and leaves everything running in the foreground until Ctrl-C (which stops the six service
processes; the Docker containers keep running — `docker compose down` separately, or run
`platform-test/scripts/stop-all.sh` from another shell to stop both). Same underlying
`lib/e2e-common.sh` the `e2e-*.sh` scenario scripts below use, so this is the same environment
those tests run against, just left up for manual `curl`/Temporal-Web-UI/pgAdmin/debugger use.

**Startup log check**: right after each service reports `/actuator/health` `UP`, both
`start-all.sh` and every `e2e-*.sh` scenario grep its `target/e2e-logs/<scenario>/<module>.log` for
an `] ERROR ` logback line and abort the whole run if one is found (`check_service_log_for_startup_errors`
in `lib/e2e-common.sh`) — `UP` alone doesn't prove a clean start, since a bean-initialization
problem can log an error without stopping the app context. Apply the same check by hand
(`grep '] ERROR ' <logfile>`) whenever starting services manually instead (below), or via
`spring-boot:run`, where nothing automates it.

**Manual**, one terminal per service, with Local infra (above) up:

```bash
./mvnw install -pl saga-common -DskipTests -q
./mvnw spring-boot:run -pl booking-service      # &, or one terminal per service
./mvnw spring-boot:run -pl flight-service
./mvnw spring-boot:run -pl hotel-service
./mvnw spring-boot:run -pl car-service
./mvnw spring-boot:run -pl payment-service
./mvnw spring-boot:run -pl saga-orchestrator-temporal
```

`booking-service` and `saga-orchestrator-temporal` need `TEMPORAL_TARGET` (defaults to
`localhost:7234`, matching Local infra above); every business service needs the same `DB_*` env
vars pattern as `temporal-saga-pattern`'s services. `saga-orchestrator-temporal` defaults its five
`services.*.base-url` entries to each service's Local infra port (see the port table above) via
`FLIGHT_SERVICE_URL`/`HOTEL_SERVICE_URL`/`CAR_SERVICE_URL`/`PAYMENT_SERVICE_URL`/`BOOKING_SERVICE_URL`
— override only if running a service on a non-default port.

With all six up (either way), `POST /travel-bookings` on `booking-service` (port 8091) drives a
real saga end to end. Two automated e2e suites do the same thing without manual `curl`:
`platform-test`'s `TravelBookingEndToEndSagaIT` (Current state, above; JUnit + Testcontainers, no
Docker Compose needed) and `platform-test/scripts/e2e-happy-path.sh` (plain bash + `curl` against
the real `docker-compose.yml` stack, `bootstrap_environment` + `post_travel_booking` +
`wait_booking_status` from `lib/e2e-common.sh`, then asserts every leg's reservation status and the
payment hold's status directly via `psql`) — mirrors `temporal-saga-pattern`'s own
`e2e-happy-path.sh`. Unlike that sibling suite, there's only one `e2e-*.sh` scenario here: its
temporary/permanent-fault scripts rely on fake-failure injection built into `inventory-service`/
`payment-service` (SKU/amount markers that trigger a simulated timeout or rejection) that this
repo's real, non-simulated business services don't have — every scenario this suite can currently
drive is a variant of the happy path.

## Explicitly deferred

Everything listed under "Open items" above, plus (mirroring `temporal-saga-pattern`'s own
`CLAUDE.md` structure):

- A shared library between this repo and `temporal-saga-pattern` — same "not yet" as that repo's own
  relationship to `saga-pattern-poc`.
- Refund (`use-case-errors.md`, E4) and everything downstream of it.
- Status query (`GET /travel-bookings/{sagaId}`) — `use-case-alternatives.md` A2, not yet decided
  whether it's even in scope.
