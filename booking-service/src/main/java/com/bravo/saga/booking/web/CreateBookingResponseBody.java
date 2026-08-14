package com.bravo.saga.booking.web;

import com.bravo.saga.booking.domain.TravelBookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Response body for {@code POST /travel-bookings} — 202 on first submission, 200 on idempotent replay. */
public record CreateBookingResponseBody(
        @Schema(description = "The Saga's own id — also the persisted booking's id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(description = "The booking's idempotency key (client-supplied or generated)", requiredMode = Schema.RequiredMode.REQUIRED)
        String businessKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TravelBookingStatus status) {

    public CreateBookingResponseBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be blank");
        }
    }
}
