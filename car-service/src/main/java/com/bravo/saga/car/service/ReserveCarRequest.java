package com.bravo.saga.car.service;

import com.bravo.saga.common.workflow.CarBookingRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link CarReservationProgressionService#reserveCar(ReserveCarRequest)}. */
public record ReserveCarRequest(UUID sagaId,
        CarBookingRequest carBooking,
        Instant now) {

    public ReserveCarRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(carBooking, "carBooking must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
