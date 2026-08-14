package com.bravo.saga.booking.domain;

import java.util.UUID;

/**
 * Thrown by a cancellation attempt against a booking already in {@link TravelBookingStatus#CONFIRMED}
 * — docs/travel-booking/controller-layer-plan.md, Operation 2: cancelling a confirmed booking would
 * need a refund, which isn't supported yet.
 */
public final class BookingAlreadyConfirmedException extends RuntimeException {

    public BookingAlreadyConfirmedException(final UUID sagaId) {
        super("Booking " + sagaId + " is already confirmed and paid; cancellation after confirmation "
                + "requires a refund, which is not yet supported");
    }
}
