package com.bravo.saga.orchestrator.activities;

import java.time.LocalDate;
import java.util.UUID;

/** Body sent to {@code flight-service}'s {@code POST /flight-reservations}. */
record FlightReservationRequest(UUID sagaId,
        String flightNumber,
        String originAirportCode,
        String destinationAirportCode,
        LocalDate departureDate,
        Integer passengerCount,
        UUID offerReference) {
}
