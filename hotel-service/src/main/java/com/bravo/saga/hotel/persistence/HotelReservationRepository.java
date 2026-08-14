package com.bravo.saga.hotel.persistence;

import com.bravo.saga.hotel.domain.HotelReservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/** Reactive persistence gateway for {@link HotelReservation} aggregates (table {@code hotel_reservation}). */
public interface HotelReservationRepository extends ReactiveCrudRepository<HotelReservation, UUID> {
}
