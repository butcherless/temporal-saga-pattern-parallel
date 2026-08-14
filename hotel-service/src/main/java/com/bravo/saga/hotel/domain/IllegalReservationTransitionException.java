package com.bravo.saga.hotel.domain;

/** Thrown by {@link HotelReservation} when asked to transition to a status its current one disallows. */
public final class IllegalReservationTransitionException extends RuntimeException {

    public IllegalReservationTransitionException(final ReservationStatus from,
            final ReservationStatus to) {
        super("Cannot transition hotel reservation from " + from + " to " + to);
    }
}
