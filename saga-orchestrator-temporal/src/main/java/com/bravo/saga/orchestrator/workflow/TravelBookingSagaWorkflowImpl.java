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
import java.util.function.Supplier;

/**
 * The travel-booking saga's happy path (docs/travel-booking/use-case-main.md, steps 1-8): two
 * independent 3-way fan-outs — reservations, then confirmations — gated by one sequential payment
 * step in between. Neither {@code temporal-saga-pattern}'s {@code OrderSagaWorkflowImpl} (four
 * sequential Activity calls) nor a fully-independent fan-out has this shape; that's the whole point
 * of this repo's comparison — see the root pom's {@code <description>}.
 *
 * <p><b>Compensation status</b> (docs/travel-booking/use-case-errors.md):
 * <ul>
 *     <li>E1 (a reservation fails before the hold), E2 (the hold itself fails), and E3 (a
 *     confirmation fails after the hold) are all implemented below.</li>
 *     <li>E3's compensation is a payment <b>hold release</b> only — capture hasn't happened, so no
 *     refund is owed (use-case-errors.md, E3). Leg reservations are left as-is: a {@code CONFIRMED}
 *     leg cannot be un-confirmed ({@code ReservationStatus.CONFIRMED} is terminal; the leg
 *     services' cancel endpoint rejects it), and a leg that only reserved is a valid Failed-booking
 *     end state.</li>
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

        final List<ReservationLeg> legs = this.startReservations(input);
        awaitReservationsOrCompensate(legs);

        try {
            this.paymentActivities.holdPayment(input.sagaId(), input.paymentAuthorizationToken());
        } catch (final RuntimeException holdFailed) {
            // E2: the hold is only attempted once all three reservations are already known to have
            // succeeded, so every leg is compensated (use-case-errors.md, E2).
            cancelInParallel(legs.stream().map(ReservationLeg::compensation).toList());
            throw holdFailed;
        }

        // Stage 2 — parallel confirmations (main use case, step 5).
        final Promise<Void> flightConfirmed = Async.procedure(this.flightActivities::confirmFlight, input.sagaId());
        final Promise<Void> hotelConfirmed = Async.procedure(this.hotelActivities::confirmHotel, input.sagaId());
        final Promise<Void> carConfirmed = Async.procedure(this.carActivities::confirmCar, input.sagaId());
        try {
            Promise.allOf(flightConfirmed, hotelConfirmed, carConfirmed).get();
        } catch (final RuntimeException confirmationFailed) {
            // E3 (use-case-errors.md): a confirmation failed after the hold. Money has moved but not
            // been captured, so the compensation owed is a hold *release*, not a refund. Leg
            // reservations are left as-is (see this class's javadoc): a confirmed leg is terminal
            // and cannot be unwound, and a reserved-not-confirmed leg is a valid Failed end state.
            this.paymentActivities.releasePaymentHold(input.sagaId());
            throw confirmationFailed;
        }

        this.paymentActivities.capturePayment(input.sagaId());
        this.bookingActivities.confirmBooking(input.sagaId());
    }

    /**
     * One reservation leg in flight: the {@link Promise} for its {@code reserve*} Activity, paired
     * with how to compensate it — a {@link Supplier} that, when invoked, issues the leg's
     * {@code cancel*Reservation} Activity and returns its {@link Promise}. Deferred as a
     * {@code Supplier} rather than an eager {@code Promise} so a leg that never needs cancelling
     * never issues the call.
     */
    private record ReservationLeg(Promise<Void> reserved,
            Supplier<Promise<Void>> compensation) {
    }

    /** Stage 1 — issues all three {@code reserve*} Activities in parallel (main use case, step 2). */
    private List<ReservationLeg> startReservations(final TravelBookingSagaInput input) {
        return List.of(
                new ReservationLeg(
                        Async.procedure(this.flightActivities::reserveFlight, input.sagaId(), input.flightBooking()),
                        () -> Async.procedure(this.flightActivities::cancelFlightReservation, input.sagaId())),
                new ReservationLeg(
                        Async.procedure(this.hotelActivities::reserveHotel, input.sagaId(), input.hotelBooking()),
                        () -> Async.procedure(this.hotelActivities::cancelHotelReservation, input.sagaId())),
                new ReservationLeg(
                        Async.procedure(this.carActivities::reserveCar, input.sagaId(), input.carBooking()),
                        () -> Async.procedure(this.carActivities::cancelCarReservation, input.sagaId())));
    }

    /**
     * E1 compensation (use-case-errors.md): waits for every leg's reservation, inspecting each
     * {@link Promise} individually rather than via {@code Promise.allOf(...).get()} — which would
     * surface only the first failure and leave the other legs' outcomes unknown. If all succeed,
     * returns. If any fail, cancels the legs that <em>did</em> succeed in parallel (order between
     * them doesn't matter — the legs are independent), waits for those compensations, then rethrows
     * the first reservation failure in leg order.
     */
    private static void awaitReservationsOrCompensate(final List<ReservationLeg> legs) {
        RuntimeException firstFailure = null;
        final List<Supplier<Promise<Void>>> succeededLegCompensations = new ArrayList<>();

        for (final ReservationLeg leg : legs) {
            try {
                leg.reserved().get();
                succeededLegCompensations.add(leg.compensation());
            } catch (final RuntimeException reservationFailed) {
                if (firstFailure == null) {
                    firstFailure = reservationFailed;
                }
            }
        }

        if (firstFailure == null) {
            return;
        }
        cancelInParallel(succeededLegCompensations);
        throw firstFailure;
    }

    /** Issues every compensation (starting them all in parallel), then waits for all of them. */
    private static void cancelInParallel(final List<Supplier<Promise<Void>>> compensations) {
        final List<Promise<Void>> pending = new ArrayList<>();
        for (final Supplier<Promise<Void>> compensation : compensations) {
            pending.add(compensation.get());
        }
        pending.forEach(Promise::get);
    }

    @Override
    public void cancel() {
        log.debug("cancel");
        this.cancellationRequested = true;
    }
}
