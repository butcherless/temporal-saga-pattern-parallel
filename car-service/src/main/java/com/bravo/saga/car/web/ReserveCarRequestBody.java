package com.bravo.saga.car.web;

import com.bravo.saga.common.workflow.CarBookingRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Request body for {@code POST /car-reservations}, called by the saga Workflow's CarActivities. */
public record ReserveCarRequestBody(
        @Schema(description = "The saga this reservation belongs to; also the reservation's own id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(example = "COMPACT", requiredMode = Schema.RequiredMode.REQUIRED)
        String carCategory,
        @Schema(example = "CDG", requiredMode = Schema.RequiredMode.REQUIRED)
        String pickupLocation,
        @Schema(example = "2026-09-10", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate pickupDate,
        @Schema(example = "2026-09-14", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dropoffDate,
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) {

    public ReserveCarRequestBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(carCategory, "carCategory must not be null");
        Objects.requireNonNull(pickupLocation, "pickupLocation must not be null");
        Objects.requireNonNull(pickupDate, "pickupDate must not be null");
        Objects.requireNonNull(dropoffDate, "dropoffDate must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
    }

    /** Converts to the saga-common leg type the service layer consumes, delegating field validation to it. */
    public CarBookingRequest toCarBookingRequest() {
        return new CarBookingRequest(this.carCategory, this.pickupLocation, this.pickupDate, this.dropoffDate, this.offerReference);
    }
}
