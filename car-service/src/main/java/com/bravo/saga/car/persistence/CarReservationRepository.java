package com.bravo.saga.car.persistence;

import com.bravo.saga.car.domain.CarReservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/** Reactive persistence gateway for {@link CarReservation} aggregates (table {@code car_reservation}). */
public interface CarReservationRepository extends ReactiveCrudRepository<CarReservation, UUID> {
}
