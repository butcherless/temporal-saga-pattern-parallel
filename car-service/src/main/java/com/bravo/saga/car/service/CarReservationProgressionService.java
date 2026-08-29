package com.bravo.saga.car.service;

import com.bravo.saga.car.domain.CarReservation;
import com.bravo.saga.car.persistence.CarReservationRepository;
import com.bravo.saga.leg.domain.IllegalReservationTransitionException;
import com.bravo.saga.leg.domain.ReservationStatus;
import com.bravo.saga.leg.service.SagaStepRequest;
import com.bravo.saga.leg.service.SimulatedTransientReservationFaultException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Car Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5, and compensation (use-case-errors.md E1/E2): reserve, confirm, and cancel a car
 * reservation. Backs {@code CarActivities}, called by the saga Workflow, once
 * {@code car-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
 */
@Slf4j
public class CarReservationProgressionService {

    /**
     * A reserved offer reference (never issued by a real provider) that triggers a one-time
     * simulated transient fault on {@link #reserveCar} — this repo otherwise has no fault
     * injection mechanism, unlike {@code temporal-saga-pattern}'s SKU/amount markers (see
     * CLAUDE.md). Exists to exercise the Temporal Activity retry path manually/in e2e scripts
     * without needing a real provider outage.
     */
    public static final UUID SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CarReservationRepository carReservationRepository;
    private final Set<UUID> sagaIdsWithSimulatedFaultAlreadyTriggered = ConcurrentHashMap.newKeySet();

    public CarReservationProgressionService(final CarReservationRepository carReservationRepository) {
        this.carReservationRepository = Objects.requireNonNull(carReservationRepository, "carReservationRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<CarReservation> reserveCar(final ReserveCarRequest request) {
        log.debug("reserveCar - {}", request);
        if (SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE.equals(request.carBooking().offerReference())
                && this.sagaIdsWithSimulatedFaultAlreadyTriggered.add(request.sagaId())) {
            log.warn("reserveCar - simulating a one-time transient fault for sagaId={}", request.sagaId());
            return Mono.error(new SimulatedTransientReservationFaultException(request.sagaId()));
        }
        return this.carReservationRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.carReservationRepository.save(
                        CarReservation.reserve(request.sagaId(), request.carBooking(), request.now()))));
    }

    /**
     * Confirms the reservation, or returns it unchanged if already {@link ReservationStatus#CONFIRMED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be confirmed (e.g. already {@code CANCELLED})
     */
    public Mono<CarReservation> confirmCar(final SagaStepRequest request) {
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
    public Mono<CarReservation> cancelCarReservation(final SagaStepRequest request) {
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
