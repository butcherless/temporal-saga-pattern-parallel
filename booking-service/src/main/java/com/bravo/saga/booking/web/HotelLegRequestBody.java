package com.bravo.saga.booking.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** The {@code hotel} block of {@code POST /travel-bookings} — no price field, see {@link CreateBookingRequestBody}. */
public record HotelLegRequestBody(
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
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider, valid for 10 minutes from issuance",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) {

    public HotelLegRequestBody {
        Objects.requireNonNull(hotelCode, "hotelCode must not be null");
        Objects.requireNonNull(roomType, "roomType must not be null");
        Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        Objects.requireNonNull(checkOutDate, "checkOutDate must not be null");
        Objects.requireNonNull(guestCount, "guestCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        if (hotelCode.isBlank()) {
            throw new IllegalArgumentException("hotelCode must not be blank");
        }
        if (guestCount <= 0) {
            throw new IllegalArgumentException("guestCount must be positive");
        }
        if (!checkInDate.isBefore(checkOutDate)) {
            throw new IllegalArgumentException("checkInDate must be before checkOutDate");
        }
    }
}
