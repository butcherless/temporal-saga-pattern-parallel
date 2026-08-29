package com.bravo.saga.leg.domain;

/** Thrown by a reservation aggregate when asked to transition to a status its current one disallows. */
public final class IllegalReservationTransitionException extends RuntimeException {

    public IllegalReservationTransitionException(final ReservationStatus from,
            final ReservationStatus to) {
        super("Cannot transition reservation from " + from + " to " + to);
    }
}
