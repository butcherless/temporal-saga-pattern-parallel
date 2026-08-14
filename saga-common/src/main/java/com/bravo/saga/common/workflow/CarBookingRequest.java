package com.bravo.saga.common.workflow;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The car leg of a {@link TravelBookingSagaInput} — no price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference} at
 * the provider and is never carried through this saga as a separate value.
 */
public record CarBookingRequest(
        String carCategory,
        String pickupLocation,
        LocalDate pickupDate,
        LocalDate dropoffDate,
        UUID offerReference) {

    public CarBookingRequest {
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
