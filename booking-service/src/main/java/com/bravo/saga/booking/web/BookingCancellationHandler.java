package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.service.CancelBookingRequest;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Objects;
import java.util.UUID;

/**
 * Backs {@code POST /travel-bookings/{sagaId}/cancel} (docs/travel-booking/controller-layer-plan.md,
 * Operation 2): validates the booking hasn't reached {@code Confirmed}/{@code Failed}
 * (rejecting with a domain exception the {@code RestExceptionHandler} maps to 409 otherwise), then
 * sends the {@link TravelBookingSagaWorkflow#cancel()} signal to the running Workflow Execution,
 * looked up by {@code businessKey} — the actual Workflow Id, per {@link BookingCreationHandler}.
 *
 * <p><b>Known gap:</b> once a signal is actually sent, nothing yet flips the booking row to
 * {@code Cancelled} — that depends on the orchestrator acting on the signal, which it doesn't yet
 * (see {@link TravelBookingSagaWorkflow#cancel()}'s javadoc). In practice this means the
 * {@code CANCELLED} branch below is currently unreachable — nothing in this codebase produces it
 * yet — but the check is kept so the behavior is already correct once that gap closes: a booking
 * already {@code Cancelled} is not re-signalled, matching the response body's own status field
 * (which {@link BookingController} uses to choose 200 vs. 202, per the controller-layer plan).
 */
@Slf4j
public class BookingCancellationHandler {

    private final TravelBookingProgressionService travelBookingProgressionService;
    private final WorkflowClient workflowClient;
    private final Scheduler blockingCallScheduler;

    public BookingCancellationHandler(
            final TravelBookingProgressionService travelBookingProgressionService,
            final WorkflowClient workflowClient,
            final Scheduler blockingCallScheduler) {
        this.travelBookingProgressionService = Objects.requireNonNull(travelBookingProgressionService, "travelBookingProgressionService must not be null");
        this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient must not be null");
        this.blockingCallScheduler = Objects.requireNonNull(blockingCallScheduler, "blockingCallScheduler must not be null");
    }

    public Mono<BookingStatusResponseBody> cancelBooking(final UUID sagaId,
            final CancelBookingRequestBody request) {
        log.debug("cancelBooking - sagaId={}, request={}", sagaId, request);

        return travelBookingProgressionService.validateCancellable(new CancelBookingRequest(sagaId, request.reason()))
                .flatMap(booking -> booking.status() == TravelBookingStatus.CANCELLED
                        ? Mono.just(new BookingStatusResponseBody(booking.id(), booking.status()))
                        : sendCancelSignal(booking.businessKey())
                                .thenReturn(new BookingStatusResponseBody(booking.id(), booking.status())));
    }

    private Mono<Void> sendCancelSignal(final String businessKey) {
        final WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(businessKey);

        // WorkflowStub.signal is a blocking gRPC call; run it off the WebFlux event loop, same
        // bridge pattern as BookingCreationHandler's WorkflowClient.start call.
        return Mono.fromRunnable(() -> workflowStub.signal("cancel"))
                .subscribeOn(blockingCallScheduler)
                .then();
    }
}
