package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.service.CreateBookingRequest;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.common.workflow.SagaTaskQueues;
import com.bravo.saga.common.workflow.TravelBookingSagaInput;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Backs {@code POST /travel-bookings} (docs/travel-booking/controller-layer-plan.md, Operation 1):
 * creates the booking, then starts the {@link TravelBookingSagaWorkflow} (Workflow Id =
 * {@code businessKey}, same convention as {@code temporal-saga-pattern}'s {@code OrderCreationHandler}
 * — restarting the saga for an already-started businessKey is a client-side no-op rather than a
 * duplicate execution).
 */
@Slf4j
public class BookingCreationHandler {

    private final TravelBookingProgressionService travelBookingProgressionService;
    private final WorkflowClient workflowClient;
    private final Scheduler blockingCallScheduler;

    public BookingCreationHandler(
            final TravelBookingProgressionService travelBookingProgressionService,
            final WorkflowClient workflowClient,
            final Scheduler blockingCallScheduler) {
        this.travelBookingProgressionService = Objects.requireNonNull(travelBookingProgressionService, "travelBookingProgressionService must not be null");
        this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient must not be null");
        this.blockingCallScheduler = Objects.requireNonNull(blockingCallScheduler, "blockingCallScheduler must not be null");
    }

    public Mono<BookingCreationOutcome> createBooking(final CreateBookingRequestBody request) {
        log.debug("createBooking - {}", request);
        final UUID sagaId = UUID.randomUUID();
        final String businessKey = request.hasNoBusinessKey() ? sagaId.toString() : request.businessKey();
        final Instant now = Instant.now();

        return this.travelBookingProgressionService.createBooking(new CreateBookingRequest(sagaId, businessKey, request.operatorId(), now))
                .flatMap(booking -> {
                    final boolean newlyCreated = booking.id().equals(sagaId);
                    return this.startSagaIfNewlyCreated(booking, newlyCreated, request)
                            .thenReturn(new BookingCreationOutcome(
                                    new CreateBookingResponseBody(booking.id(), booking.businessKey(), booking.status()),
                                    newlyCreated));
                });
    }

    /**
     * Starts the saga only for the call that actually created the booking — {@code newlyCreated}
     * is false whenever the progression service returned an already-existing row instead
     * (idempotent by businessKey). {@link WorkflowExecutionAlreadyStarted} is swallowed as a
     * defense against the narrow race where two concurrent duplicate requests both observe no
     * existing booking — either way, the caller sees the same successful, idempotent outcome.
     */
    private Mono<Void> startSagaIfNewlyCreated(final TravelBooking booking,
            final boolean newlyCreated,
            final CreateBookingRequestBody request) {
        if (!newlyCreated) {
            return Mono.empty();
        }

        final TravelBookingSagaWorkflow workflow = this.workflowClient.newWorkflowStub(
                TravelBookingSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(booking.businessKey())
                        .setTaskQueue(SagaTaskQueues.TRAVEL_BOOKING_SAGA)
                        .build());
        final TravelBookingSagaInput input = new TravelBookingSagaInput(
                booking.id(),
                booking.businessKey(),
                booking.operatorId(),
                request.paymentAuthorizationToken(),
                toFlightBookingRequest(request.flight()),
                toHotelBookingRequest(request.hotel()),
                toCarBookingRequest(request.car()));

        // WorkflowClient.start is a blocking gRPC call; run it off the WebFlux event loop.
        return Mono.fromRunnable(() -> WorkflowClient.start(workflow::process, input))
                .subscribeOn(this.blockingCallScheduler)
                .onErrorResume(WorkflowExecutionAlreadyStarted.class, error -> Mono.empty())
                .then();
    }

    private static FlightBookingRequest toFlightBookingRequest(final FlightLegRequestBody flight) {
        return new FlightBookingRequest(flight.flightNumber(), flight.originAirportCode(), flight.destinationAirportCode(),
                flight.departureDate(), flight.passengerCount(), flight.offerReference());
    }

    private static HotelBookingRequest toHotelBookingRequest(final HotelLegRequestBody hotel) {
        return new HotelBookingRequest(hotel.hotelCode(), hotel.roomType(), hotel.checkInDate(),
                hotel.checkOutDate(), hotel.guestCount(), hotel.offerReference());
    }

    private static CarBookingRequest toCarBookingRequest(final CarLegRequestBody car) {
        return new CarBookingRequest(car.carCategory(), car.pickupLocation(), car.pickupDate(),
                car.dropoffDate(), car.offerReference());
    }

    /**
     * {@code newlyCreated} is internal wiring, not part of the JSON response — it's how
     * {@link BookingController} picks 202 (a new saga started) vs. 200 (idempotent replay of an
     * existing booking), per docs/travel-booking/controller-layer-plan.md, Operation 1.
     */
    public record BookingCreationOutcome(CreateBookingResponseBody body,
            boolean newlyCreated) {
    }
}
