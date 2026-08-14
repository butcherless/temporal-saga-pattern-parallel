package com.bravo.saga.flight.domain;

/** Thrown by {@link FlightReservation} when asked to transition to a status its current one disallows. */
public final class IllegalReservationTransitionException extends RuntimeException {

    public IllegalReservationTransitionException(final ReservationStatus from,
            final ReservationStatus to) {
        super("Cannot transition flight reservation from " + from + " to " + to);
    }
}
