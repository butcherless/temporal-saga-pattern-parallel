package com.bravo.saga.common.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Contract shared between whoever starts the travel-booking saga ({@code booking-service}) and the
 * Worker that executes it ({@code saga-orchestrator-temporal}).
 *
 * <p>Deliberately no {@code @QueryMethod} yet — unlike {@code OrderSagaWorkflow}'s
 * {@code getProgress()}, an in-flight status query for this saga is still an open item
 * (docs/travel-booking/use-case-alternatives.md, A2, pending).
 */
@WorkflowInterface
public interface TravelBookingSagaWorkflow {

    @WorkflowMethod
    void process(TravelBookingSagaInput input);

    /**
     * Backs {@code POST /travel-bookings/{sagaId}/cancel} (docs/travel-booking/controller-layer-plan.md,
     * Operation 2) — only meaningful while the booking hasn't reached {@code Confirmed}; the
     * caller enforces that precondition before sending this signal, not the Workflow itself.
     *
     * <p><b>Known gap, not yet wired into {@code process()}:</b> the Workflow implementation
     * currently receives this signal but does not yet act on it — compensating whichever
     * reservations already succeeded once cancellation is requested depends on error scenarios
     * E1/E2/E3 (docs/travel-booking/use-case-errors.md), which are themselves still PENDING.
     */
    @SignalMethod
    void cancel();
}
