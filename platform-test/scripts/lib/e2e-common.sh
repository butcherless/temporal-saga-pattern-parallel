#!/usr/bin/env bash
#
# Shared functions for the curl-based e2e scenario scripts in ../*.sh. Not runnable on its own —
# each scenario script sets SCENARIO_NAME, then sources this file, then calls bootstrap_environment
# and post_travel_booking. Portable to bash 3.2 (macOS's default /bin/bash): parallel indexed
# arrays, no `declare -A`. Mirrors temporal-saga-pattern/platform-test/scripts/lib/e2e-common.sh's
# shape; see the per-function comments below for where and why this repo's version differs — mainly
# six services instead of four, and a parallel three-leg fan-out instead of a single reservation.
#
# Requirements: docker (with the "compose" plugin), curl, java 25 on PATH, and this project's
# ./mvnw.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

: "${SCENARIO_NAME:?SCENARIO_NAME must be set before sourcing e2e-common.sh}"
readonly LOG_DIR="$REPO_ROOT/platform-test/target/e2e-logs/$SCENARIO_NAME"

readonly POSTGRES_CONTAINER="saga-parallel-postgres"
readonly TEMPORAL_CONTAINER="saga-parallel-temporal"
readonly TEMPORAL_NETWORK="temporal-saga-pattern-parallel_default"
readonly TEMPORAL_ADMIN_IMAGE="temporalio/admin-tools:1.31.2"

# Parallel arrays (not an associative array: the default /bin/bash on macOS is 3.2, which
# predates `declare -A`) — SERVICE_PORTS[i] is the port for SERVICES[i].
readonly SERVICES=(booking-service flight-service hotel-service car-service payment-service saga-orchestrator-temporal)
readonly SERVICE_PORTS=(8091 8092 8093 8094 8095 8090)
# The Temporal Worker starts executing any queued Workflow Task the moment it connects — i.e.
# calling the business services' REST endpoints immediately — so it must start last, same reasoning
# as temporal-saga-pattern's CLAUDE.md "Starting the full stack".
readonly START_ORDER=(booking-service flight-service hotel-service car-service payment-service saga-orchestrator-temporal)
readonly HEALTH_TIMEOUT_S=60
readonly SAGA_TIMEOUT_S=60

PIDS=()

log() { echo "[e2e] $*"; }
fail() { echo "[e2e] FAIL: $*" >&2; exit 1; }

cleanup() {
    local exit_code=$?
    for pid in "${PIDS[@]:-}"; do
        kill "$pid" >/dev/null 2>&1 || true
    done
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

port_for_module() {
    local target=$1 i
    for i in "${!SERVICES[@]}"; do
        if [[ "${SERVICES[$i]}" == "$target" ]]; then
            echo "${SERVICE_PORTS[$i]}"
            return 0
        fi
    done
    fail "unknown module: $target"
}

wait_container_healthy() {
    local container=$1 waited=0
    while true; do
        local health_status
        health_status=$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
        [[ "$health_status" == "healthy" ]] && return 0
        waited=$((waited + 2))
        [[ $waited -ge $HEALTH_TIMEOUT_S ]] && fail "container $container did not become healthy within ${HEALTH_TIMEOUT_S}s (last status: $health_status)"
        sleep 2
    done
}

# docker-compose.yml's `temporal` service has no healthcheck (unlike postgres), so readiness is
# proven by successfully talking gRPC to it. Registering the "default" namespace this way both
# waits for Temporal to be ready *and* performs the one-time setup saga-orchestrator-temporal needs
# to start at all (production-style temporalio/server, unlike the dev-mode `server start-dev`
# platform-test's Testcontainers-backed IT uses, never creates it automatically).
wait_temporal_ready_and_register_namespace() {
    local waited=0 output
    while true; do
        if output=$(docker run --rm --network "$TEMPORAL_NETWORK" "$TEMPORAL_ADMIN_IMAGE" \
                temporal operator namespace create --address "$TEMPORAL_CONTAINER:7233" default 2>&1); then
            log "Registered the \"default\" Temporal namespace."
            return 0
        fi
        if echo "$output" | grep -qi "already exists"; then
            log "Temporal \"default\" namespace already exists."
            return 0
        fi
        waited=$((waited + 2))
        [[ $waited -ge $HEALTH_TIMEOUT_S ]] && fail "Temporal Server did not become ready within ${HEALTH_TIMEOUT_S}s (last error: $output)"
        sleep 2
    done
}

free_port() {
    local port=$1
    local pid
    pid=$(lsof -ti "tcp:$port" 2>/dev/null || true)
    if [[ -n "$pid" ]]; then
        log "Killing stale process on port $port (pid $pid)"
        kill -9 $pid 2>/dev/null || true
    fi
}

start_service() {
    local module=$1 port jar
    port=$(port_for_module "$module")
    jar="$REPO_ROOT/$module/target/$module-$VERSION-exec.jar"
    [[ -f "$jar" ]] || fail "jar not found: $jar (build step did not produce it)"
    java -jar "$jar" --spring.profiles.active=local >"$LOG_DIR/$module.log" 2>&1 &
    PIDS+=($!)
    log "Started $module (pid $!, port $port), logging to $LOG_DIR/$module.log"
}

wait_service_healthy() {
    local module=$1 port waited=0
    port=$(port_for_module "$module")
    while true; do
        if curl -sf "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"'; then
            return 0
        fi
        waited=$((waited + 1))
        [[ $waited -ge $HEALTH_TIMEOUT_S ]] && fail "$module on port $port did not become healthy within ${HEALTH_TIMEOUT_S}s (see $LOG_DIR/$module.log)"
        sleep 1
    done
}

# /actuator/health can report UP while a service's own log already recorded a startup problem
# (e.g. a bean-initialization warning that didn't stop the app context, or an Activity registration
# failure logged but not fatal) — health alone doesn't prove a clean start. Checked right after
# wait_service_healthy for every service in bootstrap_environment, below. Matches on the logback
# level token specifically (`] ERROR `), not the bare substring "error", so a business-domain
# message that merely mentions the word (there are none today, but a future one might) can't cause
# a false failure.
check_service_log_for_startup_errors() {
    local module=$1 log_file="$LOG_DIR/$module.log" errors
    errors=$(grep -E '\] ERROR ' "$log_file" || true)
    if [[ -n "$errors" ]]; then
        log "$module logged ERROR-level output during startup:"
        echo "$errors" | sed 's/^/[e2e]   /'
        fail "$module logged ERROR-level output during startup (see $log_file)"
    fi
}

psql_query() {
    local db=$1 sql=$2
    docker exec -i "$POSTGRES_CONTAINER" psql -U saga -d "$db" -tAc "$sql" | tr -d '[:space:]'
}

assert_status() {
    local label=$1 actual=$2 expected=$3
    [[ "$actual" == "$expected" ]] || fail "$label = '$actual', expected '$expected'"
    log "$label = $actual"
}

# The terminal signal available over plain psql is travel_booking.status itself:
# TravelBookingSagaWorkflowImpl always ends by confirming the booking on the happy path (or leaves
# it PENDING/fails the Workflow Execution on the paths this repo doesn't compensate yet — see
# CLAUDE.md), so polling it *is* polling the saga's outcome. No saga_instance table: Temporal's own
# server tracks the Workflow Execution, this repo has no application-owned state machine on top.
wait_booking_status() {
    local saga_id=$1 expected=$2 waited=0 last=""
    while true; do
        last=$(psql_query booking_db "SELECT status FROM travel_booking WHERE id = '$saga_id'")
        [[ "$last" == "$expected" ]] && return 0
        waited=$((waited + 1))
        [[ $waited -ge $SAGA_TIMEOUT_S ]] && fail "booking sagaId=$saga_id did not reach $expected within ${SAGA_TIMEOUT_S}s (last seen: ${last:-<no row>})"
        sleep 1
    done
}

# Exercises the A2 status-query endpoint (GET /travel-bookings/{sagaId}) over HTTP, asserting it
# reports $expected — the same value wait_booking_status just read straight from Postgres, now
# proven to also come back correctly through booking-service's own read path.
assert_booking_status_via_http() {
    local saga_id=$1 expected=$2 response http_code body status
    response=$(curl -sS -w '\n%{http_code}' "http://localhost:$(port_for_module booking-service)/travel-bookings/$saga_id")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    [[ "$http_code" == "200" ]] || fail "GET /travel-bookings/$saga_id returned $http_code, expected 200 (body: $body)"
    status=$(echo "$body" | grep -oE '"status"[[:space:]]*:[[:space:]]*"[^"]+"' | grep -oE '"[A-Z]+"$' | tr -d '"')
    [[ "$status" == "$expected" ]] || fail "GET /travel-bookings/$saga_id reported status=$status, expected $expected (body: $body)"
    log "GET /travel-bookings/$saga_id => 200, status=$status"
}

# Recreates Postgres+Temporal from scratch, rebuilds the six module jars (saga-common plus the
# five business services and the orchestrator), starts the five business services then the
# Temporal Worker last, and waits for all six to report healthy. Identical across every scenario
# script.
bootstrap_environment() {
    mkdir -p "$LOG_DIR"

    require_command docker
    require_command curl
    require_command java
    require_command lsof

    log "Recreating Postgres + Temporal from a clean slate..."
    docker compose down -v
    docker compose up -d postgres temporal temporal-schema-setup temporal-ui
    wait_container_healthy "$POSTGRES_CONTAINER"
    wait_temporal_ready_and_register_namespace
    log "Postgres + Temporal are ready."

    local module
    for module in "${SERVICES[@]}"; do
        free_port "$(port_for_module "$module")"
    done

    log "Building executable jars (unit tests skipped)..."
    ./mvnw clean verify -DskipTests \
        -pl saga-common,booking-service,flight-service,hotel-service,car-service,payment-service,saga-orchestrator-temporal \
        -am -q -B

    VERSION=$(grep -m1 -oE '<version>[^<]+</version>' pom.xml | sed -E 's#</?version>##g')
    log "Resolved project version: $VERSION"

    for module in "${START_ORDER[@]}"; do
        start_service "$module"
        wait_service_healthy "$module"
        check_service_log_for_startup_errors "$module"
    done
    log "All six services are healthy, and none logged an ERROR during startup (business services first, Temporal Worker last)."
}

# Sentinel offerReference recognized by flight-service/hotel-service/car-service's
# *ReservationProgressionService (SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE, never issued by a
# real provider): the first reserve call for a given sagaId against that leg fails, every
# subsequent call (including Temporal's automatic Activity retry) succeeds normally. Used by
# e2e-temporary-fault.sh below.
readonly SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE="00000000-0000-0000-0000-000000000001"

# POST /travel-bookings with a freshly generated businessKey (so re-runs never collide with a
# previous run's idempotency state) and a fixed, always-valid flight/hotel/car leg. An optional 1st
# argument overrides the generated businessKey — used by scenarios that need to POST the same
# businessKey twice. Sets SAGA_ID and BUSINESS_KEY on success; fails if the response isn't 202 on
# first submission.
post_travel_booking() {
    local business_key=${1:-"E2E-$SCENARIO_NAME-$(date +%Y%m%d%H%M%S)-$RANDOM"}
    post_travel_booking_with_key "$business_key"
}

# Same as post_travel_booking, but the flight leg's businessKey resolves to
# SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE instead of a fresh UUID — used by
# e2e-temporary-fault.sh to make the flight reservation fail once and retry.
post_travel_booking_with_flight_fault() {
    local business_key=${1:-"E2E-$SCENARIO_NAME-$(date +%Y%m%d%H%M%S)-$RANDOM"}
    post_travel_booking_with_key "$business_key" "$SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE"
}

# 2nd argument optionally overrides the flight leg's offerReference (defaults to a fresh UUID, same
# as every call before this override existed).
post_travel_booking_with_key() {
    local business_key=$1 flight_offer_reference=${2:-$(uuid_v4)}
    BUSINESS_KEY="$business_key"
    log "POST /travel-bookings (businessKey=$BUSINESS_KEY)..."
    local response http_code body
    response=$(curl -sS -w '\n%{http_code}' -X POST "http://localhost:$(port_for_module booking-service)/travel-bookings" \
        -H 'Content-Type: application/json' \
        -d "{
              \"operatorId\": \"agency-042\",
              \"paymentAuthorizationToken\": \"pat_9f3e2b7c8a1d4e5f\",
              \"businessKey\": \"$BUSINESS_KEY\",
              \"flight\": {
                \"flightNumber\": \"IB6275\", \"originAirportCode\": \"MAD\", \"destinationAirportCode\": \"CDG\",
                \"departureDate\": \"2026-09-10\", \"passengerCount\": 2, \"offerReference\": \"$flight_offer_reference\"
              },
              \"hotel\": {
                \"hotelCode\": \"HTL-PARIS-014\", \"roomType\": \"DOUBLE_DELUXE\",
                \"checkInDate\": \"2026-09-10\", \"checkOutDate\": \"2026-09-14\", \"guestCount\": 2, \"offerReference\": \"$(uuid_v4)\"
              },
              \"car\": {
                \"carCategory\": \"COMPACT\", \"pickupLocation\": \"CDG\",
                \"pickupDate\": \"2026-09-10\", \"dropoffDate\": \"2026-09-14\", \"offerReference\": \"$(uuid_v4)\"
              }
            }")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    [[ "$http_code" == "202" || "$http_code" == "200" ]] || fail "POST /travel-bookings returned $http_code, expected 202 (body: $body)"

    SAGA_ID=$(echo "$body" | grep -oE '"sagaId"[[:space:]]*:[[:space:]]*"[^"]+"' | grep -oE '"[0-9a-fA-F-]{36}"' | tr -d '"')
    [[ -n "$SAGA_ID" ]] || fail "could not extract sagaId from response body: $body"
    log "sagaId=$SAGA_ID (http $http_code)"
}

# A distinct offerReference per leg per call keeps each POST /travel-bookings visibly independent
# in the reservation tables even across repeat runs — not a business requirement (offerReference
# isn't validated against anything real here), just avoids every run's rows looking identical.
uuid_v4() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        python3 -c 'import uuid; print(uuid.uuid4())'
    fi
}
