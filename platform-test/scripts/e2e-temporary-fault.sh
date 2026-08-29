#!/usr/bin/env bash
#
# ==============================================================================================
# Scenario: a transient fault during the flight reservation (step 2 of
# docs/travel-booking/use-case-main.md), retried, and the saga still completes.
#
# Business context: the flight leg's reservation call fails once — e.g. a momentary provider
# blip — while hotel and car succeed on their first try. Temporal's default Activity retry policy
# redelivers the failed reserveFlight call; the retry succeeds (idempotent by sagaId, same as any
# other call), and the saga proceeds exactly as the happy path would from there: payment held, all
# three legs confirmed, payment captured, booking confirmed.
#
# Fault injection: the flight leg's offerReference is set to
# SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE (lib/e2e-common.sh) — a sentinel
# FlightReservationProgressionService#reserveFlight recognizes, failing exactly once per sagaId
# then behaving normally. Not a business feature — this repo's only fault-injection mechanism,
# added specifically to make this scenario possible (see CLAUDE.md).
#
# Expected result: same postconditions as the happy path — travel_booking CONFIRMED;
# flight_reservation/hotel_reservation/car_reservation all CONFIRMED; payment_hold CAPTURED —
# reached only after the flight leg's first reserveFlight attempt failed and was retried.
# ==============================================================================================
#
# Usage: platform-test/scripts/e2e-temporary-fault.sh

SCENARIO_NAME="temporary-fault"
source "$(dirname "${BASH_SOURCE[0]}")/lib/e2e-common.sh"

bootstrap_environment

post_travel_booking_with_flight_fault

log "Waiting for the booking to reach CONFIRMED (despite the flight leg's first reserve attempt failing)..."
wait_booking_status "$SAGA_ID" CONFIRMED

log "Querying the A2 status endpoint over HTTP..."
assert_booking_status_via_http "$SAGA_ID" CONFIRMED

log "Verifying the flight-service log actually recorded the simulated fault and a retry..."
FLIGHT_LOG="$LOG_DIR/flight-service.log"
grep -q "simulating a one-time transient fault for sagaId=$SAGA_ID" "$FLIGHT_LOG" \
    || fail "flight-service.log has no record of the simulated fault for sagaId=$SAGA_ID (see $FLIGHT_LOG)"
log "flight-service.log confirms the simulated fault fired for sagaId=$SAGA_ID."

log "Verifying each business service's final state..."
FLIGHT_STATUS=$(psql_query flight_db "SELECT status FROM flight_reservation WHERE id = '$SAGA_ID'")
assert_status "flight_reservation.status" "$FLIGHT_STATUS" "CONFIRMED"

HOTEL_STATUS=$(psql_query hotel_db "SELECT status FROM hotel_reservation WHERE id = '$SAGA_ID'")
assert_status "hotel_reservation.status" "$HOTEL_STATUS" "CONFIRMED"

CAR_STATUS=$(psql_query car_db "SELECT status FROM car_reservation WHERE id = '$SAGA_ID'")
assert_status "car_reservation.status" "$CAR_STATUS" "CONFIRMED"

PAYMENT_STATUS=$(psql_query payment_db "SELECT status FROM payment_hold WHERE id = '$SAGA_ID'")
assert_status "payment_hold.status" "$PAYMENT_STATUS" "CAPTURED"

log "PASS: temporary-fault saga survived a retried flight reservation and completed end-to-end (businessKey=$BUSINESS_KEY, sagaId=$SAGA_ID)"
