package com.bravo.saga.leg.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The reservation-aggregate surface the shared {@code LegReservationProgressionService} drives:
 * read the id and status, and produce the confirmed/cancelled next state. Implemented by
 * {@code FlightReservation}, {@code HotelReservation}, and {@code CarReservation} (each records
 * itself as {@code SELF}).
 *
 * @param <SELF> the implementing aggregate type, so {@link #confirm}/{@link #cancel} stay typed
 */
public interface LegReservation<SELF extends LegReservation<SELF>> {

    UUID id();

    ReservationStatus status();

    /** @throws IllegalReservationTransitionException if {@link #status()} cannot become {@link ReservationStatus#CONFIRMED} */
    SELF confirm(Instant now);

    /** @throws IllegalReservationTransitionException if {@link #status()} cannot become {@link ReservationStatus#CANCELLED} */
    SELF cancel(Instant now);
}
