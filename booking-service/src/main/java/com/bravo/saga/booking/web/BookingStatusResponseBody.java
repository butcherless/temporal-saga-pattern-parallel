package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Response body shared by {@code /cancel} and {@code /confirm}: the booking's own id and its current status. */
public record BookingStatusResponseBody(
        @Schema(description = "The saga's own id — also the booking's id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TravelBookingStatus status) {

    public BookingStatusResponseBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
