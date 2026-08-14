package com.bravo.saga.hotel.service;

import com.bravo.saga.common.workflow.HotelBookingRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link HotelReservationProgressionService#reserveHotel(ReserveHotelRequest)}. */
public record ReserveHotelRequest(UUID sagaId,
        HotelBookingRequest hotelBooking,
        Instant now) {

    public ReserveHotelRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(hotelBooking, "hotelBooking must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
