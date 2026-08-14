package com.bravo.saga.booking.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** The {@code flight} block of {@code POST /travel-bookings} — no price field, see {@link CreateBookingRequestBody}. */
public record FlightLegRequestBody(
        @Schema(example = "IB6275", requiredMode = Schema.RequiredMode.REQUIRED)
        String flightNumber,
        @Schema(example = "MAD", requiredMode = Schema.RequiredMode.REQUIRED)
        String originAirportCode,
        @Schema(example = "CDG", requiredMode = Schema.RequiredMode.REQUIRED)
        String destinationAirportCode,
        @Schema(example = "2026-09-10", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate departureDate,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer passengerCount,
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider, valid for 10 minutes from issuance",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) {

    public FlightLegRequestBody {
        Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        Objects.requireNonNull(originAirportCode, "originAirportCode must not be null");
        Objects.requireNonNull(destinationAirportCode, "destinationAirportCode must not be null");
        Objects.requireNonNull(departureDate, "departureDate must not be null");
        Objects.requireNonNull(passengerCount, "passengerCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        if (flightNumber.isBlank()) {
            throw new IllegalArgumentException("flightNumber must not be blank");
        }
        if (passengerCount <= 0) {
            throw new IllegalArgumentException("passengerCount must be positive");
        }
    }
}
