package com.bravo.saga.flight.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link FlightReservationProgressionService#cancelFlightReservation(CancelFlightReservationRequest)}. */
public record CancelFlightReservationRequest(UUID sagaId,
        Instant now) {

    public CancelFlightReservationRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
