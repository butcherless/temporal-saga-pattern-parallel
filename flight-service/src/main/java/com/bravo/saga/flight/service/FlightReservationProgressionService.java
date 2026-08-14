package com.bravo.saga.flight.service;

import com.bravo.saga.flight.domain.FlightReservation;
import com.bravo.saga.flight.domain.IllegalReservationTransitionException;
import com.bravo.saga.flight.domain.ReservationStatus;
import com.bravo.saga.flight.persistence.FlightReservationRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Flight Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5, and compensation (use-case-errors.md E1/E2): reserve, confirm, and cancel a flight
 * reservation. Backs {@code FlightActivities}, called by the saga Workflow, once
 * {@code flight-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
 */
@Slf4j
public class FlightReservationProgressionService {

    private final FlightReservationRepository flightReservationRepository;

    public FlightReservationProgressionService(final FlightReservationRepository flightReservationRepository) {
        this.flightReservationRepository = Objects.requireNonNull(flightReservationRepository, "flightReservationRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<FlightReservation> reserveFlight(final ReserveFlightRequest request) {
        log.debug("reserveFlight - {}", request);
        return this.flightReservationRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.flightReservationRepository.save(
                        FlightReservation.reserve(request.sagaId(), request.flightBooking(), request.now()))));
    }

    /**
     * Confirms the reservation, or returns it unchanged if already {@link ReservationStatus#CONFIRMED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be confirmed (e.g. already {@code CANCELLED})
     */
    public Mono<FlightReservation> confirmFlight(final ConfirmFlightRequest request) {
        log.debug("confirmFlight - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CONFIRMED
                        ? Mono.just(reservation)
                        : this.flightReservationRepository.save(reservation.confirm(request.now())));
    }

    /**
     * Cancels the reservation (compensation), or returns it unchanged if already {@link ReservationStatus#CANCELLED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be cancelled (e.g. already {@code CONFIRMED})
     */
    public Mono<FlightReservation> cancelFlightReservation(final CancelFlightReservationRequest request) {
        log.debug("cancelFlightReservation - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CANCELLED
                        ? Mono.just(reservation)
                        : this.flightReservationRepository.save(reservation.cancel(request.now())));
    }

    private Mono<FlightReservation> loadReservation(final UUID sagaId) {
        return this.flightReservationRepository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Flight reservation not found: %s".formatted(sagaId))));
    }
}
