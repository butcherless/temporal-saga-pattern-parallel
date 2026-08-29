package com.bravo.saga.flight.service;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.flight.domain.FlightReservation;
import com.bravo.saga.flight.persistence.FlightReservationRepository;
import com.bravo.saga.leg.service.LegReservationProgressionService;

/**
 * Flight Service's reservation lifecycle — reserve/confirm/cancel, idempotent by {@code sagaId}.
 * All behaviour lives in {@link LegReservationProgressionService}; this only binds the flight
 * aggregate/booking types, the {@link FlightReservation#reserve} factory, and the leg name.
 */
public class FlightReservationProgressionService
        extends LegReservationProgressionService<FlightReservation, FlightBookingRequest> {

    public FlightReservationProgressionService(final FlightReservationRepository flightReservationRepository) {
        super(flightReservationRepository, FlightReservation::reserve, "Flight");
    }
}
