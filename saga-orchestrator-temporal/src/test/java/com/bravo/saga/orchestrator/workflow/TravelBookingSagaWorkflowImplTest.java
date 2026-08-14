package com.bravo.saga.orchestrator.workflow;

import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.common.workflow.TravelBookingSagaInput;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import com.bravo.saga.orchestrator.activities.BookingActivities;
import com.bravo.saga.orchestrator.activities.CarActivities;
import com.bravo.saga.orchestrator.activities.FlightActivities;
import com.bravo.saga.orchestrator.activities.HotelActivities;
import com.bravo.saga.orchestrator.activities.PaymentActivities;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link TravelBookingSagaWorkflowImpl} against fake Activities via
 * {@link TestWorkflowEnvironment} — no real Temporal Server, no HTTP. Mockito mocks can't stand in
 * for Activity implementations here (same reasoning as {@code temporal-saga-pattern}'s
 * {@code OrderSagaWorkflowImplTest}): the SDK rejects Mockito's generated mock subclass because it
 * re-carries {@code @ActivityMethod} on the overridden method. {@link FakeActivities} implements
 * all five Activity interfaces at once so a single {@code registerActivitiesImplementations} call
 * covers the whole Workflow.
 */
class TravelBookingSagaWorkflowImplTest {

    private static final String TASK_QUEUE = "test-travel-booking-saga-task-queue";

    private final List<String> callLog = new CopyOnWriteArrayList<>();
    private TestWorkflowEnvironment testEnv;

    @AfterEach
    void tearDown() {
        this.testEnv.close();
    }

    /**
     * The main use case's happy path (docs/travel-booking/use-case-main.md, steps 1-8): reserve all
     * three legs in parallel, hold payment, confirm all three legs in parallel, capture, confirm
     * the booking. The three legs run concurrently via {@code Async.procedure} so their relative
     * order with each other isn't asserted — only that each phase as a whole precedes the next.
     */
    @Test
    void processRunsAllStepsOfTheMainUseCaseInTheCorrectOrder() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).build());
        workflow.process(input);

        assertThat(this.callLog).containsExactlyInAnyOrder(
                "reserveFlight(%s)".formatted(sagaId),
                "reserveHotel(%s)".formatted(sagaId),
                "reserveCar(%s)".formatted(sagaId),
                "holdPayment(%s)".formatted(sagaId),
                "confirmFlight(%s)".formatted(sagaId),
                "confirmHotel(%s)".formatted(sagaId),
                "confirmCar(%s)".formatted(sagaId),
                "capturePayment(%s)".formatted(sagaId),
                "confirmBooking(%s)".formatted(sagaId));

        this.assertBefore(sagaId, "reserveFlight", "holdPayment");
        this.assertBefore(sagaId, "reserveHotel", "holdPayment");
        this.assertBefore(sagaId, "reserveCar", "holdPayment");
        this.assertBefore(sagaId, "holdPayment", "confirmFlight");
        this.assertBefore(sagaId, "holdPayment", "confirmHotel");
        this.assertBefore(sagaId, "holdPayment", "confirmCar");
        this.assertBefore(sagaId, "confirmFlight", "capturePayment");
        this.assertBefore(sagaId, "confirmHotel", "capturePayment");
        this.assertBefore(sagaId, "confirmCar", "capturePayment");
        this.assertBefore(sagaId, "capturePayment", "confirmBooking");
    }

    /**
     * E1 (use-case-errors.md): one leg's reservation fails permanently before the payment hold is
     * ever attempted — the other two, already-succeeded legs must be cancelled, the failed leg must
     * not be (nothing to cancel there), and payment must never be touched.
     */
    @Test
    void processCompensatesTheSucceededLegsWhenOneReservationFailsPermanently() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failReserveHotelPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog).containsExactlyInAnyOrder(
                "reserveFlight(%s)".formatted(sagaId),
                "reserveHotel(%s)".formatted(sagaId),
                "reserveCar(%s)".formatted(sagaId),
                "cancelFlightReservation(%s)".formatted(sagaId),
                "cancelCarReservation(%s)".formatted(sagaId));
    }

    /**
     * E2 (use-case-errors.md): all three reservations succeed, but the payment hold itself fails
     * permanently — all three already-succeeded legs must be cancelled.
     */
    @Test
    void processCompensatesAllThreeLegsWhenTheHoldFailsPermanently() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failHoldPaymentPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog).containsExactlyInAnyOrder(
                "reserveFlight(%s)".formatted(sagaId),
                "reserveHotel(%s)".formatted(sagaId),
                "reserveCar(%s)".formatted(sagaId),
                "holdPayment(%s)".formatted(sagaId),
                "cancelFlightReservation(%s)".formatted(sagaId),
                "cancelHotelReservation(%s)".formatted(sagaId),
                "cancelCarReservation(%s)".formatted(sagaId));
    }

    private void assertBefore(final UUID sagaId,
            final String earlierCallPrefix,
            final String laterCallPrefix) {
        final int earlierIndex = this.callLog.indexOf("%s(%s)".formatted(earlierCallPrefix, sagaId));
        final int laterIndex = this.callLog.indexOf("%s(%s)".formatted(laterCallPrefix, sagaId));
        assertThat(earlierIndex)
                .describedAs("%s should run before %s in %s", earlierCallPrefix, laterCallPrefix, this.callLog)
                .isNotEqualTo(-1)
                .isLessThan(laterIndex);
    }

    private TravelBookingSagaWorkflow newWorkflowStub(final FakeActivities activities) {
        this.testEnv = TestWorkflowEnvironment.newInstance();
        final Worker worker = this.testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TravelBookingSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        this.testEnv.start();
        return this.testEnv.getWorkflowClient().newWorkflowStub(
                TravelBookingSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    }

    private static TravelBookingSagaInput defaultInput(final UUID sagaId) {
        return new TravelBookingSagaInput(sagaId, "biz-2026-08-13-0001", "agency-042", "pat_9f3e2b7c8a1d4e5f",
                new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID()),
                new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID()),
                new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID()));
    }

    /**
     * A single recording+fault-injecting fake covering all five Activity interfaces this Workflow
     * uses. None of this class's tests assert on failure messages, only on the {@code callLog}
     * contents/ordering and on whether the Workflow ultimately fails — so every permanent fault
     * below shares one generic message.
     */
    private static final class FakeActivities implements FlightActivities, HotelActivities, CarActivities, PaymentActivities, BookingActivities {

        private final List<String> callLog;
        private final boolean reserveHotelFailsPermanently;
        private final boolean holdPaymentFailsPermanently;

        private FakeActivities(final Builder builder) {
            this.callLog = builder.callLog;
            this.reserveHotelFailsPermanently = builder.reserveHotelFailsPermanently;
            this.holdPaymentFailsPermanently = builder.holdPaymentFailsPermanently;
        }

        static Builder builder(final List<String> callLog) {
            return new Builder(callLog);
        }

        @Override
        public void reserveFlight(final UUID sagaId,
                final FlightBookingRequest flightBooking) {
            this.callLog.add("reserveFlight(%s)".formatted(sagaId));
        }

        @Override
        public void confirmFlight(final UUID sagaId) {
            this.callLog.add("confirmFlight(%s)".formatted(sagaId));
        }

        @Override
        public void cancelFlightReservation(final UUID sagaId) {
            this.callLog.add("cancelFlightReservation(%s)".formatted(sagaId));
        }

        @Override
        public void reserveHotel(final UUID sagaId,
                final HotelBookingRequest hotelBooking) {
            this.callLog.add("reserveHotel(%s)".formatted(sagaId));
            if (this.reserveHotelFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent hotel reservation failure", "PermanentSagaException");
            }
        }

        @Override
        public void confirmHotel(final UUID sagaId) {
            this.callLog.add("confirmHotel(%s)".formatted(sagaId));
        }

        @Override
        public void cancelHotelReservation(final UUID sagaId) {
            this.callLog.add("cancelHotelReservation(%s)".formatted(sagaId));
        }

        @Override
        public void reserveCar(final UUID sagaId,
                final CarBookingRequest carBooking) {
            this.callLog.add("reserveCar(%s)".formatted(sagaId));
        }

        @Override
        public void confirmCar(final UUID sagaId) {
            this.callLog.add("confirmCar(%s)".formatted(sagaId));
        }

        @Override
        public void cancelCarReservation(final UUID sagaId) {
            this.callLog.add("cancelCarReservation(%s)".formatted(sagaId));
        }

        @Override
        public void holdPayment(final UUID sagaId,
                final String paymentAuthorizationToken) {
            this.callLog.add("holdPayment(%s)".formatted(sagaId));
            if (this.holdPaymentFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent payment hold failure", "PermanentSagaException");
            }
        }

        @Override
        public void capturePayment(final UUID sagaId) {
            this.callLog.add("capturePayment(%s)".formatted(sagaId));
        }

        @Override
        public void releasePaymentHold(final UUID sagaId) {
            this.callLog.add("releasePaymentHold(%s)".formatted(sagaId));
        }

        @Override
        public void confirmBooking(final UUID sagaId) {
            this.callLog.add("confirmBooking(%s)".formatted(sagaId));
        }

        private static final class Builder {
            private final List<String> callLog;
            private boolean reserveHotelFailsPermanently;
            private boolean holdPaymentFailsPermanently;

            private Builder(final List<String> callLog) {
                this.callLog = callLog;
            }

            Builder failReserveHotelPermanently() {
                this.reserveHotelFailsPermanently = true;
                return this;
            }

            Builder failHoldPaymentPermanently() {
                this.holdPaymentFailsPermanently = true;
                return this;
            }

            FakeActivities build() {
                return new FakeActivities(this);
            }
        }
    }
}
