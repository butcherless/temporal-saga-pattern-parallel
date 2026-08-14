package com.bravo.saga.booking.service;

import com.bravo.saga.booking.domain.BookingAlreadyConfirmedException;
import com.bravo.saga.booking.domain.BookingAlreadyFailedException;
import com.bravo.saga.booking.domain.BookingNotFoundException;
import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.persistence.TravelBookingRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Persistence-level transitions for {@link TravelBooking} — idempotency-by-{@code businessKey}/
 * {@code sagaId} discipline (docs/travel-booking/business-definitions.md §3), same shape as
 * {@code temporal-saga-pattern}'s {@code OrderProgressionService}.
 */
@Slf4j
public class TravelBookingProgressionService {

    private final TravelBookingRepository travelBookingRepository;

    public TravelBookingProgressionService(final TravelBookingRepository travelBookingRepository) {
        this.travelBookingRepository = Objects.requireNonNull(travelBookingRepository, "travelBookingRepository must not be null");
    }

    /** Idempotent by {@code businessKey} (main use case, Idempotency table): a duplicate call returns the existing row unchanged. */
    public Mono<TravelBooking> createBooking(final CreateBookingRequest request) {
        log.debug("createBooking - {}", request);
        return travelBookingRepository.findByBusinessKey(request.businessKey())
                .switchIfEmpty(Mono.defer(() -> travelBookingRepository.save(
                        TravelBooking.create(request.sagaId(), request.businessKey(), request.operatorId(), request.now()))));
    }

    /**
     * Confirms the booking — called by {@code BookingActivities} from the saga Workflow once
     * payment has been captured (main use case, step 8). Idempotent by {@code sagaId}: a booking
     * already {@code Confirmed} is returned unchanged rather than re-transitioned.
     */
    public Mono<TravelBooking> confirmBooking(final ConfirmBookingRequest request) {
        log.debug("confirmBooking - {}", request);
        return travelBookingRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.error(new BookingNotFoundException(request.sagaId())))
                .flatMap(booking -> booking.status() == TravelBookingStatus.CONFIRMED
                        ? Mono.just(booking)
                        : travelBookingRepository.save(booking.confirm(request.now())));
    }

    /**
     * Validates a booking is eligible for cancellation — docs/travel-booking/controller-layer-plan.md,
     * Operation 2: only while not yet {@code Confirmed}. Deliberately read-only: it does not
     * transition the row to {@code Cancelled} itself. The actual signal to the running saga Workflow
     * is sent by the web layer ({@code BookingCancellationHandler}) after this check passes; the row
     * only reaches {@code Cancelled} once the orchestrator reports compensation complete — not yet
     * wired (see {@code TravelBookingSagaWorkflow#cancel}'s javadoc).
     */
    public Mono<TravelBooking> validateCancellable(final CancelBookingRequest request) {
        log.debug("validateCancellable - {}", request);
        return travelBookingRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.error(new BookingNotFoundException(request.sagaId())))
                .flatMap(booking -> switch (booking.status()) {
                    case CONFIRMED -> Mono.error(new BookingAlreadyConfirmedException(request.sagaId()));
                    case FAILED -> Mono.error(new BookingAlreadyFailedException(request.sagaId()));
                    case PENDING, CANCELLED -> Mono.just(booking);
                });
    }
}
