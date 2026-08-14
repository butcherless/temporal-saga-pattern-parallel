package com.bravo.saga.booking.domain;

import java.util.UUID;

/**
 * Thrown by a cancellation attempt against a booking already in {@link TravelBookingStatus#FAILED}
 * — docs/travel-booking/controller-layer-plan.md, Operation 2, open item #4: treated as a conflict
 * rather than an idempotent no-op, since {@code Failed} (ended via a step failure) and
 * {@code Cancelled} (ended via an explicit request) have different meanings worth not collapsing.
 */
public final class BookingAlreadyFailedException extends RuntimeException {

    public BookingAlreadyFailedException(final UUID sagaId) {
        super("Booking " + sagaId + " already ended due to a step failure, not a cancellation request");
    }
}
