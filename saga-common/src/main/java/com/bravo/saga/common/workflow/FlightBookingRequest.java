package com.bravo.saga.common.workflow;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The flight leg of a {@link TravelBookingSagaInput} — no price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference} at
 * the provider and is never carried through this saga as a separate value.
 */
public record FlightBookingRequest(
        String flightNumber,
        String originAirportCode,
        String destinationAirportCode,
        LocalDate departureDate,
        Integer passengerCount,
        UUID offerReference) implements LegBookingRequest {

    public FlightBookingRequest {
        Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        Objects.requireNonNull(originAirportCode, "originAirportCode must not be null");
        Objects.requireNonNull(destinationAirportCode, "destinationAirportCode must not be null");
        Objects.requireNonNull(departureDate, "departureDate must not be null");
        Objects.requireNonNull(passengerCount, "passengerCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        if (flightNumber.isBlank()) {
            throw new IllegalArgumentException("flightNumber must not be blank");
        }
        if (passengerCount <= 0) {
            throw new IllegalArgumentException("passengerCount must be positive");
        }
    }
}
