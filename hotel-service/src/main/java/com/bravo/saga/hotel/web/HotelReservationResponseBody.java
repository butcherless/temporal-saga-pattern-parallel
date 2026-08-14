package com.bravo.saga.hotel.web;

import com.bravo.saga.hotel.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Response body shared by every hotel reservation endpoint: the reservation's own id (the saga's id) and its current status. */
public record HotelReservationResponseBody(
        @Schema(description = "The saga's own id — also the reservation's id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(description = "The reservation's status", requiredMode = Schema.RequiredMode.REQUIRED)
        ReservationStatus status) {

    public HotelReservationResponseBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
