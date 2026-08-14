package com.bravo.saga.common.workflow;

import java.util.Objects;
import java.util.UUID;

/**
 * Input to {@link TravelBookingSagaWorkflow#process(TravelBookingSagaInput)} — the three
 * independent legs (flight/hotel/car), plus {@code paymentAuthorizationToken}, the customer's
 * pre-existing authorization to hold/charge the total (docs/travel-booking/business-definitions.md
 * §4). No total/amount field here: this saga performs no price computation at all — see
 * docs/travel-booking/use-case-main.md.
 */
public record TravelBookingSagaInput(
        UUID sagaId,
        String businessKey,
        String operatorId,
        String paymentAuthorizationToken,
        FlightBookingRequest flightBooking,
        HotelBookingRequest hotelBooking,
        CarBookingRequest carBooking) {

    public TravelBookingSagaInput {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(paymentAuthorizationToken, "paymentAuthorizationToken must not be null");
        Objects.requireNonNull(flightBooking, "flightBooking must not be null");
        Objects.requireNonNull(hotelBooking, "hotelBooking must not be null");
        Objects.requireNonNull(carBooking, "carBooking must not be null");
        if (businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be blank");
        }
        if (paymentAuthorizationToken.isBlank()) {
            throw new IllegalArgumentException("paymentAuthorizationToken must not be blank");
        }
    }
}
