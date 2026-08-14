package com.bravo.saga.common.workflow;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The hotel leg of a {@link TravelBookingSagaInput} — no price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference} at
 * the provider and is never carried through this saga as a separate value.
 */
public record HotelBookingRequest(
        String hotelCode,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer guestCount,
        UUID offerReference) {

    public HotelBookingRequest {
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
