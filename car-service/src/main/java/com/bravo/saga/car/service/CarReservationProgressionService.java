package com.bravo.saga.car.service;

import com.bravo.saga.car.domain.CarReservation;
import com.bravo.saga.car.domain.IllegalReservationTransitionException;
import com.bravo.saga.car.domain.ReservationStatus;
import com.bravo.saga.car.persistence.CarReservationRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Car Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5, and compensation (use-case-errors.md E1/E2): reserve, confirm, and cancel a car
 * reservation. Backs {@code CarActivities}, called by the saga Workflow, once
 * {@code car-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
 */
@Slf4j
public class CarReservationProgressionService {

    private final CarReservationRepository carReservationRepository;

    public CarReservationProgressionService(final CarReservationRepository carReservationRepository) {
        this.carReservationRepository = Objects.requireNonNull(carReservationRepository, "carReservationRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<CarReservation> reserveCar(final ReserveCarRequest request) {
        log.debug("reserveCar - {}", request);
        return this.carReservationRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.carReservationRepository.save(
                        CarReservation.reserve(request.sagaId(), request.carBooking(), request.now()))));
    }

    /**
     * Confirms the reservation, or returns it unchanged if already {@link ReservationStatus#CONFIRMED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be confirmed (e.g. already {@code CANCELLED})
     */
    public Mono<CarReservation> confirmCar(final ConfirmCarRequest request) {
        log.debug("confirmCar - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CONFIRMED
                        ? Mono.just(reservation)
                        : this.carReservationRepository.save(reservation.confirm(request.now())));
    }

    /**
     * Cancels the reservation (compensation), or returns it unchanged if already {@link ReservationStatus#CANCELLED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be cancelled (e.g. already {@code CONFIRMED})
     */
    public Mono<CarReservation> cancelCarReservation(final CancelCarReservationRequest request) {
        log.debug("cancelCarReservation - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CANCELLED
                        ? Mono.just(reservation)
                        : this.carReservationRepository.save(reservation.cancel(request.now())));
    }

    private Mono<CarReservation> loadReservation(final UUID sagaId) {
        return this.carReservationRepository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Car reservation not found: %s".formatted(sagaId))));
    }
}
