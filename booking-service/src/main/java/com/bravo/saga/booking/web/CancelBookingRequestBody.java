package com.bravo.saga.booking.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for {@code POST /travel-bookings/{sagaId}/cancel} — entirely optional, per
 * docs/travel-booking/controller-layer-plan.md, Operation 2: {@code reason} is free-text, for
 * audit only, never used in any business logic.
 */
public record CancelBookingRequestBody(
        @Schema(example = "customer changed mind")
        String reason) {
}
