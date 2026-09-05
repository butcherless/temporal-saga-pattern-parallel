package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.domain.TravelBookingStatus;
import com.bravo.saga.booking.service.CancelBookingRequest;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import com.bravo.saga.common.workflow.SagaTaskQueues;
import com.bravo.saga.common.workflow.TravelBookingSagaInput;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static com.bravo.saga.booking.web.BookingTestFixtures.OPERATOR_ID;
import static com.bravo.saga.booking.web.BookingTestFixtures.sampleSagaInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link BookingCancellationHandler} — the class that actually sends the
 * {@link TravelBookingSagaWorkflow#cancel()} signal, {@code POST /travel-bookings/{sagaId}/cancel}
 * (docs/travel-booking/controller-layer-plan.md, Operation 2). {@link BookingControllerTest} mocks
 * this handler out entirely, so it is never itself exercised there — same split as
 * {@link BookingCreationHandlerTest}, and for the same reason a real {@link TestWorkflowEnvironment}
 * backs the "signal actually reaches the running Workflow" test (the SDK rejects a Mockito stand-in
 * for a workflow stub).
 */
@ExtendWith(MockitoExtension.class)
class BookingCancellationHandlerTest {

    @Mock
    private TravelBookingProgressionService travelBookingProgressionService;

    private TestWorkflowEnvironment testEnv;
    private BookingCancellationHandler handler;

    @BeforeEach
    void setUp() {
        this.testEnv = TestWorkflowEnvironment.newInstance();
        this.handler = new BookingCancellationHandler(
                this.travelBookingProgressionService, this.testEnv.getWorkflowClient(), Schedulers.immediate());
        CancellableFakeWorkflow.cancelSignalReceived = false;
    }

    @AfterEach
    void tearDown() {
        this.testEnv.close();
    }

    @Test
    void cancelBookingSignalsTheRunningWorkflowAndReturnsTheBookingsCurrentStatus() {
        final UUID sagaId = UUID.randomUUID();
        final String businessKey = "biz-2026-08-13-0007";
        final TravelBookingSagaWorkflow running = this.startFakeWorkflow(businessKey);

        final TravelBooking pending = TravelBooking.create(sagaId, businessKey, OPERATOR_ID, Instant.now());
        when(this.travelBookingProgressionService.validateCancellable(any(CancelBookingRequest.class)))
                .thenReturn(Mono.just(pending));

        StepVerifier.create(this.handler.cancelBooking(sagaId, new CancelBookingRequestBody("customer changed mind")))
                .assertNext(body -> {
                    assertThat(body.sagaId()).isEqualTo(sagaId);
                    assertThat(body.status()).isEqualTo(TravelBookingStatus.PENDING);
                })
                .verifyComplete();

        // The signal unblocks the fake Workflow's process(), so getResult returns once it lands.
        this.testEnv.getWorkflowClient().newUntypedWorkflowStub(businessKey).getResult(Void.class);
        assertThat(CancellableFakeWorkflow.cancelSignalReceived).isTrue();
    }

    @Test
    void cancelBookingDoesNotReSignalWhenTheBookingIsAlreadyCancelled() {
        final WorkflowClient mockWorkflowClient = mock(WorkflowClient.class);
        final BookingCancellationHandler handlerWithMockClient = new BookingCancellationHandler(
                this.travelBookingProgressionService, mockWorkflowClient, Schedulers.immediate());

        final UUID sagaId = UUID.randomUUID();
        final TravelBooking alreadyCancelled = new TravelBooking(
                sagaId, "biz-2026-08-13-0008", OPERATOR_ID, TravelBookingStatus.CANCELLED, Instant.now(), Instant.now(), null);
        when(this.travelBookingProgressionService.validateCancellable(any(CancelBookingRequest.class)))
                .thenReturn(Mono.just(alreadyCancelled));

        StepVerifier.create(handlerWithMockClient.cancelBooking(sagaId, new CancelBookingRequestBody(null)))
                .assertNext(body -> assertThat(body.status()).isEqualTo(TravelBookingStatus.CANCELLED))
                .verifyComplete();

        verifyNoInteractions(mockWorkflowClient);
    }

    @Test
    void cancelBookingPropagatesAValidationRejectionWithoutTouchingTheWorkflow() {
        final WorkflowClient mockWorkflowClient = mock(WorkflowClient.class);
        final BookingCancellationHandler handlerWithMockClient = new BookingCancellationHandler(
                this.travelBookingProgressionService, mockWorkflowClient, Schedulers.immediate());

        when(this.travelBookingProgressionService.validateCancellable(any(CancelBookingRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("already confirmed")));

        StepVerifier.create(handlerWithMockClient.cancelBooking(UUID.randomUUID(), new CancelBookingRequestBody(null)))
                .expectError(IllegalStateException.class)
                .verify();

        verifyNoInteractions(mockWorkflowClient);
    }

    private TravelBookingSagaWorkflow startFakeWorkflow(final String businessKey) {
        final Worker worker = this.testEnv.newWorker(SagaTaskQueues.TRAVEL_BOOKING_SAGA);
        worker.registerWorkflowImplementationTypes(CancellableFakeWorkflow.class);
        this.testEnv.start();

        final TravelBookingSagaWorkflow stub = this.testEnv.getWorkflowClient().newWorkflowStub(
                TravelBookingSagaWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(businessKey).setTaskQueue(SagaTaskQueues.TRAVEL_BOOKING_SAGA).build());
        WorkflowClient.start(stub::process, sampleSagaInput(UUID.randomUUID(), businessKey, OPERATOR_ID));
        return stub;
    }

    /** Blocks in {@code process()} until the {@code cancel} signal arrives, then completes. */
    public static class CancellableFakeWorkflow implements TravelBookingSagaWorkflow {

        private static volatile boolean cancelSignalReceived;

        private boolean cancelled;

        @Override
        public void process(final TravelBookingSagaInput input) {
            Workflow.await(() -> this.cancelled);
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            cancelSignalReceived = true;
        }
    }
}
