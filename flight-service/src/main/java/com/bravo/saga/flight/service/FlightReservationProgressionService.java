package com.bravo.saga.flight.service;

import com.bravo.saga.flight.domain.FlightReservation;
import com.bravo.saga.flight.domain.IllegalReservationTransitionException;
import com.bravo.saga.flight.domain.ReservationStatus;
import com.bravo.saga.flight.persistence.FlightReservationRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flight Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5, and compensation (use-case-errors.md E1/E2): reserve, confirm, and cancel a flight
 * reservation. Backs {@code FlightActivities}, called by the saga Workflow, once
 * {@code flight-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
 */
@Slf4j
public class FlightReservationProgressionService {

    /**
     * A reserved offer reference (never issued by a real provider) that triggers a one-time
     * simulated transient fault on {@link #reserveFlight} — this repo otherwise has no fault
     * injection mechanism, unlike {@code temporal-saga-pattern}'s SKU/amount markers (see
     * CLAUDE.md). Exists to exercise the Temporal Activity retry path manually/in e2e scripts
     * without needing a real provider outage.
     */
    public static final UUID SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FlightReservationRepository flightReservationRepository;
    private final Set<UUID> sagaIdsWithSimulatedFaultAlreadyTriggered = ConcurrentHashMap.newKeySet();

    public FlightReservationProgressionService(final FlightReservationRepository flightReservationRepository) {
        this.flightReservationRepository = Objects.requireNonNull(flightReservationRepository, "flightReservationRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<FlightReservation> reserveFlight(final ReserveFlightRequest request) {
        log.debug("reserveFlight - {}", request);
        if (SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE.equals(request.flightBooking().offerReference())
                && this.sagaIdsWithSimulatedFaultAlreadyTriggered.add(request.sagaId())) {
            log.warn("reserveFlight - simulating a one-time transient fault for sagaId={}", request.sagaId());
            return Mono.error(new SimulatedTransientReservationFaultException(request.sagaId()));
        }
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
