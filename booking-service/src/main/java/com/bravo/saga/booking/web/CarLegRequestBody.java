package com.bravo.saga.booking.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** The {@code car} block of {@code POST /travel-bookings} — no price field, see {@link CreateBookingRequestBody}. */
public record CarLegRequestBody(
        @Schema(example = "COMPACT", requiredMode = Schema.RequiredMode.REQUIRED)
        String carCategory,
        @Schema(example = "CDG", requiredMode = Schema.RequiredMode.REQUIRED)
        String pickupLocation,
        @Schema(example = "2026-09-10", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate pickupDate,
        @Schema(example = "2026-09-14", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dropoffDate,
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider, valid for 10 minutes from issuance",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) {

    public CarLegRequestBody {
        Objects.requireNonNull(carCategory, "carCategory must not be null");
        Objects.requireNonNull(pickupLocation, "pickupLocation must not be null");
        Objects.requireNonNull(pickupDate, "pickupDate must not be null");
        Objects.requireNonNull(dropoffDate, "dropoffDate must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        if (carCategory.isBlank()) {
            throw new IllegalArgumentException("carCategory must not be blank");
        }
        if (pickupLocation.isBlank()) {
            throw new IllegalArgumentException("pickupLocation must not be blank");
        }
        if (!pickupDate.isBefore(dropoffDate)) {
            throw new IllegalArgumentException("pickupDate must be before dropoffDate");
        }
    }
}
