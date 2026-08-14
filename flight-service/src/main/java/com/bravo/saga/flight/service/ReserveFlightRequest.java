package com.bravo.saga.flight.service;

import com.bravo.saga.common.workflow.FlightBookingRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link FlightReservationProgressionService#reserveFlight(ReserveFlightRequest)}. */
public record ReserveFlightRequest(UUID sagaId,
        FlightBookingRequest flightBooking,
        Instant now) {

    public ReserveFlightRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(flightBooking, "flightBooking must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
