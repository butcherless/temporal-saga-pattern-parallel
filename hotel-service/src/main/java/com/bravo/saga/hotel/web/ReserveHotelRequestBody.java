package com.bravo.saga.hotel.web;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Request body for {@code POST /hotel-reservations}, called by the saga Workflow's HotelActivities. */
public record ReserveHotelRequestBody(
        @Schema(description = "The saga this reservation belongs to; also the reservation's own id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(example = "HTL-PARIS-014", requiredMode = Schema.RequiredMode.REQUIRED)
        String hotelCode,
        @Schema(example = "DOUBLE_DELUXE", requiredMode = Schema.RequiredMode.REQUIRED)
        String roomType,
        @Schema(example = "2026-09-10", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate checkInDate,
        @Schema(example = "2026-09-14", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate checkOutDate,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer guestCount,
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) {

    public ReserveHotelRequestBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(hotelCode, "hotelCode must not be null");
        Objects.requireNonNull(roomType, "roomType must not be null");
        Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        Objects.requireNonNull(checkOutDate, "checkOutDate must not be null");
        Objects.requireNonNull(guestCount, "guestCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
    }

    /** Converts to the saga-common leg type the service layer consumes, delegating field validation to it. */
    public HotelBookingRequest toHotelBookingRequest() {
        return new HotelBookingRequest(this.hotelCode, this.roomType, this.checkInDate, this.checkOutDate, this.guestCount, this.offerReference);
    }
}
