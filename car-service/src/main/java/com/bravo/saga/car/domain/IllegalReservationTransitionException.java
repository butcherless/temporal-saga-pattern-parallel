package com.bravo.saga.car.domain;

/** Thrown by {@link CarReservation} when asked to transition to a status its current one disallows. */
public final class IllegalReservationTransitionException extends RuntimeException {

    public IllegalReservationTransitionException(final ReservationStatus from,
            final ReservationStatus to) {
        super("Cannot transition car reservation from " + from + " to " + to);
    }
}
