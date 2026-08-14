package com.bravo.saga.booking.domain;

/** Thrown by {@link TravelBooking} when asked to transition to a status its current one disallows. */
public final class IllegalTravelBookingTransitionException extends RuntimeException {

    public IllegalTravelBookingTransitionException(final TravelBookingStatus from,
            final TravelBookingStatus to) {
        super("Cannot transition travel booking from " + from + " to " + to);
    }
}
