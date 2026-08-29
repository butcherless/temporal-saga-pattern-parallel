#!/usr/bin/env bash
#
# ==============================================================================================
# Scenario: main use case, happy path (docs/travel-booking/use-case-main.md, steps 1-8)
#
# Business context: a customer buys a vacation package (flight + hotel + car). Every step succeeds
# on the first try: all three reservations are made in parallel, the payment is held, all three
# reservations are confirmed in parallel, the payment is captured, and the booking is confirmed. No
# retries, no compensation.
#
# Expected result: travel_booking CONFIRMED; flight_reservation/hotel_reservation/car_reservation
# all CONFIRMED (in their own databases); payment_hold CAPTURED.
# ==============================================================================================
#
# Curl-based end-to-end test, without JUnit/Maven driving it. Complements
# TravelBookingEndToEndSagaIT.java (JUnit + Testcontainers) with a plain-bash version against the
# real docker-compose stack. Every run recreates Postgres/Temporal from scratch and rebuilds the
# seven module jars, so it is slow (a few minutes) but self-contained and reproducible: it never
# depends on state left behind by a previous run or by a manually started `spring-boot:run`.
#
# Usage: platform-test/scripts/e2e-happy-path.sh

SCENARIO_NAME="happy-path"
source "$(dirname "${BASH_SOURCE[0]}")/lib/e2e-common.sh"

bootstrap_environment

post_travel_booking

log "Waiting for the booking to reach CONFIRMED..."
wait_booking_status "$SAGA_ID" CONFIRMED

log "Querying the A2 status endpoint over HTTP..."
assert_booking_status_via_http "$SAGA_ID" CONFIRMED

log "Verifying each business service's final state..."
FLIGHT_STATUS=$(psql_query flight_db "SELECT status FROM flight_reservation WHERE id = '$SAGA_ID'")
assert_status "flight_reservation.status" "$FLIGHT_STATUS" "CONFIRMED"

HOTEL_STATUS=$(psql_query hotel_db "SELECT status FROM hotel_reservation WHERE id = '$SAGA_ID'")
assert_status "hotel_reservation.status" "$HOTEL_STATUS" "CONFIRMED"

CAR_STATUS=$(psql_query car_db "SELECT status FROM car_reservation WHERE id = '$SAGA_ID'")
assert_status "car_reservation.status" "$CAR_STATUS" "CONFIRMED"

PAYMENT_STATUS=$(psql_query payment_db "SELECT status FROM payment_hold WHERE id = '$SAGA_ID'")
assert_status "payment_hold.status" "$PAYMENT_STATUS" "CAPTURED"

log "PASS: happy-path saga completed end-to-end (businessKey=$BUSINESS_KEY, sagaId=$SAGA_ID)"
