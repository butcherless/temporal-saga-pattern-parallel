package com.bravo.saga.booking.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link TravelBookingProgressionService#createBooking(CreateBookingRequest)}. */
public record CreateBookingRequest(UUID sagaId,
        String businessKey,
        String operatorId,
        Instant now) {

    public CreateBookingRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be blank");
        }
        if (operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId must not be blank");
        }
    }
}
