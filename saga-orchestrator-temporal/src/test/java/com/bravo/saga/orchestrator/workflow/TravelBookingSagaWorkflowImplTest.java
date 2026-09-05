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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.bravo.saga.orchestrator.activities.BookingRequestFixtures.DEFAULT_PAYMENT_TOKEN;
import static com.bravo.saga.orchestrator.activities.BookingRequestFixtures.defaultCarBooking;
import static com.bravo.saga.orchestrator.activities.BookingRequestFixtures.defaultFlightBooking;
import static com.bravo.saga.orchestrator.activities.BookingRequestFixtures.defaultHotelBooking;
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
     * E1, failure on the first leg: {@code reserveFlight} fails while hotel and car succeed. The
     * failed leg has nothing to cancel; the two survivors must both be cancelled and payment must
     * never be touched. (Complements {@link #processCompensatesTheSucceededLegsWhenOneReservationFailsPermanently},
     * where the failing leg is hotel — this one drives the flight branch of the per-leg inspection.)
     */
    @Test
    void processCompensatesHotelAndCarWhenTheFlightReservationFailsPermanently() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failReserveFlightPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog).containsExactlyInAnyOrder(
                "reserveFlight(%s)".formatted(sagaId),
                "reserveHotel(%s)".formatted(sagaId),
                "reserveCar(%s)".formatted(sagaId),
                "cancelHotelReservation(%s)".formatted(sagaId),
                "cancelCarReservation(%s)".formatted(sagaId));
    }

    /**
     * E1, two legs failing: flight and car both fail, only hotel succeeds. The lone survivor
     * (hotel) is the only leg cancelled, and the Workflow still fails. Drives the "a later leg
     * also failed but {@code firstFailure} is already set" branch of the per-leg inspection.
     */
    @Test
    void processCancelsOnlyTheLoneSurvivorWhenTwoReservationsFailPermanently() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(
                FakeActivities.builder(this.callLog).failReserveFlightPermanently().failReserveCarPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog).containsExactlyInAnyOrder(
                "reserveFlight(%s)".formatted(sagaId),
                "reserveHotel(%s)".formatted(sagaId),
                "reserveCar(%s)".formatted(sagaId),
                "cancelHotelReservation(%s)".formatted(sagaId));
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

    /**
     * E3 (use-case-errors.md): a confirmation fails after the payment hold. The compensation owed
     * is a hold <em>release</em> (capture hasn't run, so no refund) — and nothing else: leg
     * reservations are left as-is (a confirmed leg is terminal; a reserved-not-confirmed leg is a
     * valid Failed end state), so no {@code cancel*Reservation} is issued, and the saga does not
     * proceed to capture or booking confirmation.
     */
    @Test
    void processReleasesTheHoldWhenAConfirmationFailsAfterTheHold() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failConfirmHotelPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog)
                .contains("holdPayment(%s)".formatted(sagaId),
                        "confirmHotel(%s)".formatted(sagaId),
                        "releasePaymentHold(%s)".formatted(sagaId))
                .doesNotContain("capturePayment(%s)".formatted(sagaId),
                        "confirmBooking(%s)".formatted(sagaId))
                .noneSatisfy(call -> assertThat(call).startsWith("cancel"));
        this.assertBefore(sagaId, "holdPayment", "releasePaymentHold");
    }

    /**
     * E4, capture never took (use-case-errors.md): {@code capturePayment} fails permanently — since
     * capture is idempotent and retried, that means nothing was captured, so the still-{@code HELD}
     * hold is <em>released</em>, not refunded. No leg is touched, and the saga stops before booking
     * confirmation.
     */
    @Test
    void processReleasesTheHoldWhenCaptureFailsPermanently() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failCapturePaymentPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog)
                .contains("capturePayment(%s)".formatted(sagaId),
                        "releasePaymentHold(%s)".formatted(sagaId))
                .doesNotContain("refundPayment(%s)".formatted(sagaId),
                        "confirmBooking(%s)".formatted(sagaId))
                .noneSatisfy(call -> assertThat(call).startsWith("cancel"));
        this.assertBefore(sagaId, "capturePayment", "releasePaymentHold");
    }

    /**
     * E4, post-capture (use-case-errors.md): the capture succeeds, then {@code confirmBooking} fails
     * permanently — money has moved, so the compensation is a real {@code refundPayment}, not a
     * hold release. Legs are left as-is (all confirmed); no cancellation.
     */
    @Test
    void processRefundsWhenBookingConfirmationFailsAfterCapture() {
        final UUID sagaId = UUID.randomUUID();
        final TravelBookingSagaInput input = defaultInput(sagaId);

        final TravelBookingSagaWorkflow workflow = this.newWorkflowStub(FakeActivities.builder(this.callLog).failConfirmBookingPermanently().build());

        assertThatThrownBy(() -> workflow.process(input)).isInstanceOf(WorkflowFailedException.class);

        assertThat(this.callLog)
                .contains("capturePayment(%s)".formatted(sagaId),
                        "confirmBooking(%s)".formatted(sagaId),
                        "refundPayment(%s)".formatted(sagaId))
                .doesNotContain("releasePaymentHold(%s)".formatted(sagaId))
                .noneSatisfy(call -> assertThat(call).startsWith("cancel"));
        this.assertBefore(sagaId, "capturePayment", "confirmBooking");
        this.assertBefore(sagaId, "confirmBooking", "refundPayment");
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
        return new TravelBookingSagaInput(sagaId, "biz-2026-08-13-0001", "agency-042", DEFAULT_PAYMENT_TOKEN,
                defaultFlightBooking(), defaultHotelBooking(), defaultCarBooking());
    }

    /**
     * A single recording+fault-injecting fake covering all five Activity interfaces this Workflow
     * uses. None of this class's tests assert on failure messages, only on the {@code callLog}
     * contents/ordering and on whether the Workflow ultimately fails — so every permanent fault
     * below shares one generic message.
     */
    private static final class FakeActivities implements FlightActivities, HotelActivities, CarActivities, PaymentActivities, BookingActivities {

        private final List<String> callLog;
        private final boolean reserveFlightFailsPermanently;
        private final boolean reserveHotelFailsPermanently;
        private final boolean reserveCarFailsPermanently;
        private final boolean holdPaymentFailsPermanently;
        private final boolean confirmHotelFailsPermanently;
        private final boolean capturePaymentFailsPermanently;
        private final boolean confirmBookingFailsPermanently;

        private FakeActivities(final Builder builder) {
            this.callLog = builder.callLog;
            this.reserveFlightFailsPermanently = builder.reserveFlightFailsPermanently;
            this.reserveHotelFailsPermanently = builder.reserveHotelFailsPermanently;
            this.reserveCarFailsPermanently = builder.reserveCarFailsPermanently;
            this.holdPaymentFailsPermanently = builder.holdPaymentFailsPermanently;
            this.confirmHotelFailsPermanently = builder.confirmHotelFailsPermanently;
            this.capturePaymentFailsPermanently = builder.capturePaymentFailsPermanently;
            this.confirmBookingFailsPermanently = builder.confirmBookingFailsPermanently;
        }

        static Builder builder(final List<String> callLog) {
            return new Builder(callLog);
        }

        @Override
        public void reserveFlight(final UUID sagaId,
                final FlightBookingRequest flightBooking) {
            this.callLog.add("reserveFlight(%s)".formatted(sagaId));
            if (this.reserveFlightFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent flight reservation failure", "PermanentSagaException");
            }
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
            if (this.confirmHotelFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent hotel confirmation failure", "PermanentSagaException");
            }
        }

        @Override
        public void cancelHotelReservation(final UUID sagaId) {
            this.callLog.add("cancelHotelReservation(%s)".formatted(sagaId));
        }

        @Override
        public void reserveCar(final UUID sagaId,
                final CarBookingRequest carBooking) {
            this.callLog.add("reserveCar(%s)".formatted(sagaId));
            if (this.reserveCarFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent car reservation failure", "PermanentSagaException");
            }
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
            if (this.capturePaymentFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent payment capture failure", "PermanentSagaException");
            }
        }

        @Override
        public void releasePaymentHold(final UUID sagaId) {
            this.callLog.add("releasePaymentHold(%s)".formatted(sagaId));
        }

        @Override
        public void refundPayment(final UUID sagaId) {
            this.callLog.add("refundPayment(%s)".formatted(sagaId));
        }

        @Override
        public void confirmBooking(final UUID sagaId) {
            this.callLog.add("confirmBooking(%s)".formatted(sagaId));
            if (this.confirmBookingFailsPermanently) {
                throw ApplicationFailure.newNonRetryableFailure("Simulated permanent booking confirmation failure", "PermanentSagaException");
            }
        }

        private static final class Builder {
            private final List<String> callLog;
            private boolean reserveFlightFailsPermanently;
            private boolean reserveHotelFailsPermanently;
            private boolean reserveCarFailsPermanently;
            private boolean holdPaymentFailsPermanently;
            private boolean confirmHotelFailsPermanently;
            private boolean capturePaymentFailsPermanently;
            private boolean confirmBookingFailsPermanently;

            private Builder(final List<String> callLog) {
                this.callLog = callLog;
            }

            Builder failReserveFlightPermanently() {
                this.reserveFlightFailsPermanently = true;
                return this;
            }

            Builder failReserveHotelPermanently() {
                this.reserveHotelFailsPermanently = true;
                return this;
            }

            Builder failReserveCarPermanently() {
                this.reserveCarFailsPermanently = true;
                return this;
            }

            Builder failHoldPaymentPermanently() {
                this.holdPaymentFailsPermanently = true;
                return this;
            }

            Builder failConfirmHotelPermanently() {
                this.confirmHotelFailsPermanently = true;
                return this;
            }

            Builder failCapturePaymentPermanently() {
                this.capturePaymentFailsPermanently = true;
                return this;
            }

            Builder failConfirmBookingPermanently() {
                this.confirmBookingFailsPermanently = true;
                return this;
            }

            FakeActivities build() {
                return new FakeActivities(this);
            }
        }
    }
}
