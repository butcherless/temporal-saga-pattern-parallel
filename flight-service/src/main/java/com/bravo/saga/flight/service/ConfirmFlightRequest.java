package com.bravo.saga.flight.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link FlightReservationProgressionService#confirmFlight(ConfirmFlightRequest)}. */
public record ConfirmFlightRequest(UUID sagaId,
        Instant now) {

    public ConfirmFlightRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
