package com.bravo.saga.booking.service;

import java.util.Objects;
import java.util.UUID;

/** Input to {@link TravelBookingProgressionService#validateCancellable(CancelBookingRequest)}. */
public record CancelBookingRequest(UUID sagaId,
        String reason) {

    public CancelBookingRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        // reason is optional, audit-only — see docs/travel-booking/controller-layer-plan.md, Operation 2.
    }
}
