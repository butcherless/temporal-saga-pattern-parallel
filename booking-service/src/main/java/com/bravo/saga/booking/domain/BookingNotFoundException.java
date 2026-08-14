package com.bravo.saga.booking.domain;

import java.util.UUID;

/** Thrown when no {@link TravelBooking} exists for a given {@code sagaId}. */
public final class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(final UUID sagaId) {
        super("No booking found for sagaId " + sagaId);
    }
}
