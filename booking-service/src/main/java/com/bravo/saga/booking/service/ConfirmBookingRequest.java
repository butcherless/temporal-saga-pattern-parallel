package com.bravo.saga.booking.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link TravelBookingProgressionService#confirmBooking(ConfirmBookingRequest)}. */
public record ConfirmBookingRequest(UUID sagaId,
        Instant now) {

    public ConfirmBookingRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
