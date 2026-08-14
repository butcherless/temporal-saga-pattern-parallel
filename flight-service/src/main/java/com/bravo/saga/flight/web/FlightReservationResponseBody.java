package com.bravo.saga.flight.web;

import com.bravo.saga.flight.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Response body shared by every flight reservation endpoint: the reservation's own id (the saga's id) and its current status. */
public record FlightReservationResponseBody(
        @Schema(description = "The saga's own id — also the reservation's id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(description = "The reservation's status", requiredMode = Schema.RequiredMode.REQUIRED)
        ReservationStatus status) {

    public FlightReservationResponseBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
