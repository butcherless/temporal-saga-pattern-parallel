package com.bravo.saga.hotel.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link HotelReservationProgressionService#cancelHotelReservation(CancelHotelReservationRequest)}. */
public record CancelHotelReservationRequest(UUID sagaId,
        Instant now) {

    public CancelHotelReservationRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
