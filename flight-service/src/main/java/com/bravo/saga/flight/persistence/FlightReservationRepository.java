package com.bravo.saga.flight.persistence;

import com.bravo.saga.flight.domain.FlightReservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/** Reactive persistence gateway for {@link FlightReservation} aggregates (table {@code flight_reservation}). */
public interface FlightReservationRepository extends ReactiveCrudRepository<FlightReservation, UUID> {
}
