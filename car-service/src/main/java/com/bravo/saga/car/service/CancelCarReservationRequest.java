package com.bravo.saga.car.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link CarReservationProgressionService#cancelCarReservation(CancelCarReservationRequest)}. */
public record CancelCarReservationRequest(UUID sagaId,
        Instant now) {

    public CancelCarReservationRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
