package com.bravo.saga.hotel.service;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.domain.HotelReservation;
import com.bravo.saga.hotel.persistence.HotelReservationRepository;
import com.bravo.saga.leg.service.LegReservationProgressionService;

/**
 * Hotel Service's reservation lifecycle — reserve/confirm/cancel, idempotent by {@code sagaId}.
 * All behaviour lives in {@link LegReservationProgressionService}; this only binds the hotel
 * aggregate/booking types, the {@link HotelReservation#reserve} factory, and the leg name.
 */
public class HotelReservationProgressionService
        extends LegReservationProgressionService<HotelReservation, HotelBookingRequest> {

    public HotelReservationProgressionService(final HotelReservationRepository hotelReservationRepository) {
        super(hotelReservationRepository, HotelReservation::reserve, "Hotel");
    }
}
