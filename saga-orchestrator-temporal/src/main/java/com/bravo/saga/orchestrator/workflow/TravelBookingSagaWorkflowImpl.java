package com.bravo.saga.orchestrator.workflow;

import com.bravo.saga.common.workflow.TravelBookingSagaInput;
import com.bravo.saga.common.workflow.TravelBookingSagaWorkflow;
import com.bravo.saga.orchestrator.activities.BookingActivities;
import com.bravo.saga.orchestrator.activities.CarActivities;
import com.bravo.saga.orchestrator.activities.FlightActivities;
import com.bravo.saga.orchestrator.activities.HotelActivities;
import com.bravo.saga.orchestrator.activities.PaymentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The travel-booking saga's happy path (docs/travel-booking/use-case-main.md, steps 1-8): two
 * independent 3-way fan-outs — reservations, then confirmations — gated by one sequential payment
 * step in between. Neither {@code temporal-saga-pattern}'s {@code OrderSagaWorkflowImpl} (four
 * sequential Activity calls) nor a fully-independent fan-out has this shape; that's the whole point
 * of this repo's comparison — see the root pom's {@code <description>}.
 *
 * <p><b>Compensation status</b> (docs/travel-booking/use-case-errors.md):
 * <ul>
 *     <li>E1 (a reservation fails before the hold) and E2 (the hold itself fails) are implemented
 *     below — both resolved scenarios, per that doc.</li>
 *     <li>E3 (a confirmation fails after the hold) is <b>not</b> implemented — still PENDING in
 *     that doc. A confirmation failure below simply fails the Workflow Execution without attempting
 *     any compensation, which is honest about what's unresolved rather than guessing at a mechanism.</li>
 *     <li>E4 (capture fails / refund) is out of scope entirely — no refund exists yet.</li>
 *     <li>The {@link #cancel()} signal is received but not yet acted upon — see its javadoc on
 *     {@link TravelBookingSagaWorkflow}.</li>
 * </ul>
 */
public class TravelBookingSagaWorkflowImpl implements TravelBookingSagaWorkflow {

    /**
     * Not {@code @Slf4j} — a plain SLF4J logger would re-emit every line on every deterministic
     * replay (Workflow methods re-execute from the top as Temporal replays history). {@link
     * Workflow#getLogger(Class)} is replay-aware: it only actually logs on the "real" execution,
     * suppressing the replay-time duplicates. See CLAUDE.md's Logging section.
     */
    private static final Logger log = Workflow.getLogger(TravelBookingSagaWorkflowImpl.class);

    /**
     * Timeout/retry policy per step is an open item (docs/travel-booking/use-case-errors.md, open
     * questions #4) — this is a placeholder, not a considered choice, deliberately mirroring
     * {@code OrderSagaWorkflowImpl}'s own bounded-retry reasoning (a business failure must not
     * retry forever) until that item is resolved.
     */
    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build();

    private final FlightActivities flightActivities = Workflow.newActivityStub(FlightActivities.class, ACTIVITY_OPTIONS);
    private final HotelActivities hotelActivities = Workflow.newActivityStub(HotelActivities.class, ACTIVITY_OPTIONS);
    private final CarActivities carActivities = Workflow.newActivityStub(CarActivities.class, ACTIVITY_OPTIONS);
    private final PaymentActivities paymentActivities = Workflow.newActivityStub(PaymentActivities.class, ACTIVITY_OPTIONS);
    private final BookingActivities bookingActivities = Workflow.newActivityStub(BookingActivities.class, ACTIVITY_OPTIONS);

    // Received but not yet consulted anywhere below — see this class's own javadoc.
    private boolean cancellationRequested;

    @Override
    public void process(final TravelBookingSagaInput input) {
        log.debug("process - {}", input);
        this.reserveAllLegsOrCompensate(input);

        try {
            this.paymentActivities.holdPayment(input.sagaId(), input.paymentAuthorizationToken());
        } catch (final RuntimeException holdFailed) {
            // E2: the hold is only attempted once all three reservations are already known to have
            // succeeded — compensation here is deterministic, not partial (use-case-errors.md, E2).
            this.cancelReservationsInParallel(input).forEach(Promise::get);
            throw holdFailed;
        }

        // Stage 2 — parallel confirmations. A failure here is E3, still PENDING (this class's own
        // javadoc): deliberately not compensated, just propagated.
        final Promise<Void> flightConfirmed = Async.procedure(this.flightActivities::confirmFlight, input.sagaId());
        final Promise<Void> hotelConfirmed = Async.procedure(this.hotelActivities::confirmHotel, input.sagaId());
        final Promise<Void> carConfirmed = Async.procedure(this.carActivities::confirmCar, input.sagaId());
        Promise.allOf(flightConfirmed, hotelConfirmed, carConfirmed).get();

        this.paymentActivities.capturePayment(input.sagaId());
        this.bookingActivities.confirmBooking(input.sagaId());
    }

    /**
     * Stage 1 — parallel reservations (main use case, step 2) with E1 compensation
     * (use-case-errors.md): on any failure, inspects each leg's {@link Promise} individually
     * (not via {@code Promise.allOf(...).get()}, which only reports the first failure and leaves
     * the other legs' individual outcomes unknown to the caller) and cancels only the legs that
     * actually succeeded, in parallel — no ordering between them, since the legs are independent.
     */
    private void reserveAllLegsOrCompensate(final TravelBookingSagaInput input) {
        final Promise<Void> flightReserved = Async.procedure(this.flightActivities::reserveFlight, input.sagaId(), input.flightBooking());
        final Promise<Void> hotelReserved = Async.procedure(this.hotelActivities::reserveHotel, input.sagaId(), input.hotelBooking());
        final Promise<Void> carReserved = Async.procedure(this.carActivities::reserveCar, input.sagaId(), input.carBooking());

        RuntimeException firstFailure = null;
        boolean flightSucceeded = false;
        boolean hotelSucceeded = false;
        boolean carSucceeded = false;

        try {
            flightReserved.get();
            flightSucceeded = true;
        } catch (final RuntimeException error) {
            firstFailure = error;
        }
        try {
            hotelReserved.get();
            hotelSucceeded = true;
        } catch (final RuntimeException error) {
            firstFailure = firstFailure == null ? error : firstFailure;
        }
        try {
            carReserved.get();
            carSucceeded = true;
        } catch (final RuntimeException error) {
            firstFailure = firstFailure == null ? error : firstFailure;
        }

        if (firstFailure == null) {
            return;
        }

        this.cancelSucceededReservationsInParallel(input, flightSucceeded, hotelSucceeded, carSucceeded).forEach(Promise::get);
        throw firstFailure;
    }

    private List<Promise<Void>> cancelSucceededReservationsInParallel(final TravelBookingSagaInput input,
            final boolean flightSucceeded,
            final boolean hotelSucceeded,
            final boolean carSucceeded) {
        final List<Promise<Void>> compensations = new ArrayList<>();
        if (flightSucceeded) {
            compensations.add(Async.procedure(this.flightActivities::cancelFlightReservation, input.sagaId()));
        }
        if (hotelSucceeded) {
            compensations.add(Async.procedure(this.hotelActivities::cancelHotelReservation, input.sagaId()));
        }
        if (carSucceeded) {
            compensations.add(Async.procedure(this.carActivities::cancelCarReservation, input.sagaId()));
        }
        return compensations;
    }

    private List<Promise<Void>> cancelReservationsInParallel(final TravelBookingSagaInput input) {
        return this.cancelSucceededReservationsInParallel(input, true, true, true);
    }

    @Override
    public void cancel() {
        log.debug("cancel");
        this.cancellationRequested = true;
    }
}
