package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBooking;
import com.bravo.saga.booking.service.CreateBookingRequest;
import com.bravo.saga.booking.service.TravelBookingProgressionService;
import com.bravo.saga.common.workflow.SagaTaskQueues;
import com.bravo.saga.common.workflow.TravelBookingSagaInput;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
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
import java.util.concurrent.atomic.AtomicReference;

import static com.bravo.saga.booking.web.BookingTestFixtures.sampleCreateBookingRequestBody;
import static com.bravo.saga.booking.web.BookingTestFixtures.sampleSagaInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link BookingCreationHandler} — the class that actually calls
 * {@code WorkflowClient.start(...)}, main use case step 1 (docs/travel-booking/use-case-main.md).
 * {@link BookingControllerTest} mocks this handler out entirely, so it's never itself exercised
 * there. Mockito mocks can't stand in for {@link TravelBookingSagaWorkflow} stubs here — the SDK
 * inspects the proxy handed to {@code WorkflowClient.start} and rejects anything that isn't its
 * own generated proxy — so a real {@link TestWorkflowEnvironment} backs the "workflow actually
 * starts" tests, same approach as {@code temporal-saga-pattern}'s {@code OrderCreationHandlerTest}.
 */
@ExtendWith(MockitoExtension.class)
class BookingCreationHandlerTest {

    @Mock
    private TravelBookingProgressionService travelBookingProgressionService;

    private TestWorkflowEnvironment testEnv;
    private BookingCreationHandler handler;

    @BeforeEach
    void setUp() {
        this.testEnv = TestWorkflowEnvironment.newInstance();
        this.handler = new BookingCreationHandler(this.travelBookingProgressionService, this.testEnv.getWorkflowClient(), Schedulers.immediate());
    }

    @AfterEach
    void tearDown() {
        this.testEnv.close();
    }

    @Test
    void createBookingStartsTheSagaWorkflowWithThePayloadAndBusinessKeyAsWorkflowId() {
        final AtomicReference<TravelBookingSagaInput> receivedInput = new AtomicReference<>();
        this.registerFakeWorkflow(receivedInput);

        final CreateBookingRequestBody request = sampleCreateBookingRequestBody("agency-042", "biz-2026-08-13-0001");
        when(this.travelBookingProgressionService.createBooking(any())).thenAnswer(invocation -> {
            final CreateBookingRequest req = invocation.getArgument(0);
            return Mono.just(TravelBooking.create(req.sagaId(), req.businessKey(), req.operatorId(), req.now()));
        });

        StepVerifier.create(this.handler.createBooking(request))
                .assertNext(outcome -> {
                    assertThat(outcome.newlyCreated()).isTrue();
                    assertThat(outcome.body().businessKey()).isEqualTo("biz-2026-08-13-0001");
                })
                .verifyComplete();

        final WorkflowStub startedWorkflow = this.testEnv.getWorkflowClient().newUntypedWorkflowStub("biz-2026-08-13-0001");
        startedWorkflow.getResult(Void.class);

        assertThat(receivedInput.get().businessKey()).isEqualTo("biz-2026-08-13-0001");
        assertThat(receivedInput.get().operatorId()).isEqualTo("agency-042");
        assertThat(receivedInput.get().paymentAuthorizationToken()).isEqualTo("pat_9f3e2b7c8a1d4e5f");
        assertThat(receivedInput.get().flightBooking().flightNumber()).isEqualTo("IB6275");
        assertThat(receivedInput.get().hotelBooking().hotelCode()).isEqualTo("HTL-PARIS-014");
        assertThat(receivedInput.get().carBooking().carCategory()).isEqualTo("COMPACT");
    }

    @Test
    void createBookingPropagatesUncaughtErrorsWithoutStartingTheWorkflow() {
        final CreateBookingRequestBody request = sampleCreateBookingRequestBody("agency-042", null);
        when(this.travelBookingProgressionService.createBooking(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(this.handler.createBooking(request))
                .expectError(IllegalStateException.class)
                .verify();
    }

    /**
     * {@code travelBookingProgressionService.createBooking} is idempotent by businessKey, so a
     * repeat {@code POST /travel-bookings} returns the booking it created the first time — with a
     * different {@code id()} than the fresh {@code sagaId} this call minted. That mismatch is the
     * signal {@code startSagaIfNewlyCreated} uses to skip starting a second Workflow Execution
     * entirely.
     */
    @Test
    void createBookingSkipsStartingTheWorkflowWhenTheBookingAlreadyExisted() {
        final WorkflowClient mockWorkflowClient = mock(WorkflowClient.class);
        final BookingCreationHandler handlerWithMockClient =
                new BookingCreationHandler(this.travelBookingProgressionService, mockWorkflowClient, Schedulers.immediate());

        final CreateBookingRequestBody request = sampleCreateBookingRequestBody("agency-042", "biz-2026-08-13-0002");
        final TravelBooking preExistingBooking = TravelBooking.create(UUID.randomUUID(), "biz-2026-08-13-0002", "agency-042", Instant.now());
        when(this.travelBookingProgressionService.createBooking(any())).thenReturn(Mono.just(preExistingBooking));

        StepVerifier.create(handlerWithMockClient.createBooking(request))
                .assertNext(outcome -> {
                    assertThat(outcome.newlyCreated()).isFalse();
                    assertThat(outcome.body().businessKey()).isEqualTo("biz-2026-08-13-0002");
                })
                .verifyComplete();

        verifyNoInteractions(mockWorkflowClient);
    }

    /**
     * Defense against the narrow race where two concurrent duplicate requests both observe no
     * existing booking: starts a Workflow Execution for the businessKey directly (standing in for
     * the other request's in-flight saga, kept open via {@code BLOCK_FOREVER_OPERATOR_ID} so it
     * can't complete before this test's own start attempt), then drives {@code createBooking}
     * through the "newly created" branch for the same businessKey. The resulting
     * {@code WorkflowExecutionAlreadyStarted} must be swallowed as an idempotent no-op.
     */
    @Test
    void createBookingSwallowsWorkflowExecutionAlreadyStartedAsAnIdempotentNoOp() {
        final AtomicReference<TravelBookingSagaInput> receivedInput = new AtomicReference<>();
        this.registerFakeWorkflow(receivedInput);

        final String businessKey = "biz-2026-08-13-0003";
        final TravelBookingSagaWorkflow alreadyRunning = this.testEnv.getWorkflowClient().newWorkflowStub(
                TravelBookingSagaWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(businessKey).setTaskQueue(SagaTaskQueues.TRAVEL_BOOKING_SAGA).build());
        WorkflowClient.start(alreadyRunning::process, blockForeverInput(businessKey));

        final CreateBookingRequestBody request = sampleCreateBookingRequestBody("agency-042", businessKey);
        when(this.travelBookingProgressionService.createBooking(any())).thenAnswer(invocation -> {
            final CreateBookingRequest req = invocation.getArgument(0);
            return Mono.just(TravelBooking.create(req.sagaId(), req.businessKey(), req.operatorId(), req.now()));
        });

        StepVerifier.create(this.handler.createBooking(request))
                .assertNext(outcome -> assertThat(outcome.body().businessKey()).isEqualTo(businessKey))
                .verifyComplete();
    }

    private void registerFakeWorkflow(final AtomicReference<TravelBookingSagaInput> receivedInput) {
        final Worker worker = this.testEnv.newWorker(SagaTaskQueues.TRAVEL_BOOKING_SAGA);
        worker.registerWorkflowImplementationTypes(FakeTravelBookingSagaWorkflow.class);
        FakeTravelBookingSagaWorkflow.receivedInput = receivedInput;
        this.testEnv.start();
    }

    private static TravelBookingSagaInput blockForeverInput(final String businessKey) {
        return sampleSagaInput(UUID.randomUUID(), businessKey, FakeTravelBookingSagaWorkflow.BLOCK_FOREVER_OPERATOR_ID);
    }

    public static class FakeTravelBookingSagaWorkflow implements TravelBookingSagaWorkflow {

        /** Marks a fake execution that never completes, so it stays open for the duration of a test. */
        private static final String BLOCK_FOREVER_OPERATOR_ID = "agency-BLOCK-FOREVER";

        private static AtomicReference<TravelBookingSagaInput> receivedInput;

        @Override
        public void process(final TravelBookingSagaInput input) {
            receivedInput.set(input);
            if (BLOCK_FOREVER_OPERATOR_ID.equals(input.operatorId())) {
                Workflow.await(() -> false);
            }
        }

        @Override
        public void cancel() {
            // Not under test here.
        }
    }
}
