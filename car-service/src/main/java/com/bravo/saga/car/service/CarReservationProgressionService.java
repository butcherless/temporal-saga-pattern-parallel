package com.bravo.saga.car.service;

import com.bravo.saga.car.domain.CarReservation;
import com.bravo.saga.car.persistence.CarReservationRepository;
import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.leg.service.LegReservationProgressionService;

/**
 * Car Service's reservation lifecycle — reserve/confirm/cancel, idempotent by {@code sagaId}.
 * All behaviour lives in {@link LegReservationProgressionService}; this only binds the car
 * aggregate/booking types, the {@link CarReservation#reserve} factory, and the leg name.
 */
public class CarReservationProgressionService
        extends LegReservationProgressionService<CarReservation, CarBookingRequest> {

    public CarReservationProgressionService(final CarReservationRepository carReservationRepository) {
        super(carReservationRepository, CarReservation::reserve, "Car");
    }
}
