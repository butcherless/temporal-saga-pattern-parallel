package com.bravo.saga.hotel.service;

import java.util.UUID;

/**
 * Thrown once per {@code sagaId} by {@link HotelReservationProgressionService#reserveHotel} when
 * triggered via {@link HotelReservationProgressionService#SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE}.
 * Deliberately unmapped by {@code RestExceptionHandler}, so it surfaces as a 500 — the same shape a
 * real transient provider failure would take, retryable by Temporal's default Activity retry policy.
 */
public class SimulatedTransientReservationFaultException extends RuntimeException {

    public SimulatedTransientReservationFaultException(final UUID sagaId) {
        super("Simulated transient reservation fault for sagaId=%s".formatted(sagaId));
    }
}
