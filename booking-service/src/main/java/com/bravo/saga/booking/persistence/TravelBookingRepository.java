package com.bravo.saga.booking.persistence;

import com.bravo.saga.booking.domain.TravelBooking;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reactive persistence gateway for {@link TravelBooking} aggregates (table {@code travel_booking}). */
public interface TravelBookingRepository extends ReactiveCrudRepository<TravelBooking, UUID> {

    /** Looks up a booking by its idempotency key (business-definitions.md §4), independent of {@code id}. */
    Mono<TravelBooking> findByBusinessKey(String businessKey);
}
