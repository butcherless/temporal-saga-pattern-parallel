package com.bravo.saga.leg.service;

import com.bravo.saga.common.workflow.LegBookingRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Input to {@code LegReservationProgressionService#reserve}: the saga id, the leg's booking
 * details, and the clock reading. Replaces the per-leg {@code Reserve*Request} records — the leg
 * only varies in {@code B}.
 *
 * @param <B> the leg's booking-request type ({@code FlightBookingRequest} etc.)
 */
public record ReserveLegRequest<B extends LegBookingRequest>(UUID sagaId,
        B booking,
        Instant now) {

    public ReserveLegRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
