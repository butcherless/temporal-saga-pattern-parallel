package com.bravo.saga.leg.service;

import com.bravo.saga.common.workflow.LegBookingRequest;
import com.bravo.saga.leg.domain.IllegalReservationTransitionException;
import com.bravo.saga.leg.domain.LegReservation;
import com.bravo.saga.leg.domain.ReservationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reservation lifecycle every leg shares — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5 and compensation (use-case-errors.md E1/E2): reserve, confirm, cancel, all idempotent
 * by {@code sagaId}. flight-service/hotel-service/car-service each subclass this with their
 * aggregate/booking types and a factory; the leg varies only in {@code R}, {@code B}, and the
 * {@code reserve} factory.
 *
 * @param <R> the leg's reservation aggregate
 * @param <B> the leg's booking-request type
 */
@Slf4j
public class LegReservationProgressionService<R extends LegReservation<R>, B extends LegBookingRequest> {

    /**
     * A reserved offer reference (never issued by a real provider) that triggers a one-time
     * simulated transient fault on {@link #reserve} — this repo otherwise has no fault-injection
     * mechanism (see CLAUDE.md). Exists to exercise the Temporal Activity retry path
     * manually/in e2e scripts without needing a real provider outage.
     */
    public static final UUID SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Builds a brand-new {@link ReservationStatus#RESERVED} aggregate from a leg's booking details. */
    @FunctionalInterface
    public interface ReservationFactory<R, B> {

        R reserve(UUID sagaId, B booking, Instant now);
    }

    private final ReactiveCrudRepository<R, UUID> repository;
    private final ReservationFactory<R, B> reservationFactory;
    private final String legName;
    private final Set<UUID> sagaIdsWithSimulatedFaultAlreadyTriggered = ConcurrentHashMap.newKeySet();

    protected LegReservationProgressionService(final ReactiveCrudRepository<R, UUID> repository,
            final ReservationFactory<R, B> reservationFactory,
            final String legName) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.reservationFactory = Objects.requireNonNull(reservationFactory, "reservationFactory must not be null");
        this.legName = Objects.requireNonNull(legName, "legName must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<R> reserve(final ReserveLegRequest<B> request) {
        log.debug("reserve - {}", request);
        if (SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE.equals(request.booking().offerReference())
                && this.sagaIdsWithSimulatedFaultAlreadyTriggered.add(request.sagaId())) {
            log.warn("reserve - simulating a one-time transient fault for sagaId={}", request.sagaId());
            return Mono.error(new SimulatedTransientReservationFaultException(request.sagaId()));
        }
        return this.repository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.repository.save(
                        this.reservationFactory.reserve(request.sagaId(), request.booking(), request.now()))));
    }

    /**
     * Confirms the reservation, or returns it unchanged if already {@link ReservationStatus#CONFIRMED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be confirmed (e.g. already {@code CANCELLED})
     */
    public Mono<R> confirm(final SagaStepRequest request) {
        log.debug("confirm - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CONFIRMED
                        ? Mono.just(reservation)
                        : this.repository.save(reservation.confirm(request.now())));
    }

    /**
     * Cancels the reservation (compensation), or returns it unchanged if already {@link ReservationStatus#CANCELLED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be cancelled (e.g. already {@code CONFIRMED})
     */
    public Mono<R> cancel(final SagaStepRequest request) {
        log.debug("cancel - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CANCELLED
                        ? Mono.just(reservation)
                        : this.repository.save(reservation.cancel(request.now())));
    }

    private Mono<R> loadReservation(final UUID sagaId) {
        return this.repository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                        "%s reservation not found: %s".formatted(this.legName, sagaId))));
    }
}
