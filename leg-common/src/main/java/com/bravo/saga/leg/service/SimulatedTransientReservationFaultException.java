package com.bravo.saga.leg.service;

import java.util.UUID;

/**
 * Thrown once per {@code sagaId} by a leg's {@code reserve*} use case when triggered via the
 * {@code SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE} sentinel. Deliberately unmapped by
 * {@link com.bravo.saga.leg.web.LegReservationRestExceptionHandler}, so it surfaces as a 500 —
 * the same shape a real transient provider failure would take, retryable by Temporal's default
 * Activity retry policy. This repo otherwise has no fault-injection mechanism (see CLAUDE.md).
 */
public class SimulatedTransientReservationFaultException extends RuntimeException {

    public SimulatedTransientReservationFaultException(final UUID sagaId) {
        super("Simulated transient reservation fault for sagaId=%s".formatted(sagaId));
    }
}
