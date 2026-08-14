package com.bravo.saga.hotel.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link HotelReservationProgressionService#confirmHotel(ConfirmHotelRequest)}. */
public record ConfirmHotelRequest(UUID sagaId,
        Instant now) {

    public ConfirmHotelRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
