package com.bravo.saga.flight.web;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.leg.web.ReserveLegRequestBody;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Request body for {@code POST /flight-reservations}, called by the saga Workflow's FlightActivities. */
public record ReserveFlightRequestBody(
        @Schema(description = "The saga this reservation belongs to; also the reservation's own id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
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
        @Schema(description = "UUID identifying a previously-obtained, binding offer from this provider",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID offerReference) implements ReserveLegRequestBody<FlightBookingRequest> {

    public ReserveFlightRequestBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        Objects.requireNonNull(originAirportCode, "originAirportCode must not be null");
        Objects.requireNonNull(destinationAirportCode, "destinationAirportCode must not be null");
        Objects.requireNonNull(departureDate, "departureDate must not be null");
        Objects.requireNonNull(passengerCount, "passengerCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
    }

    /** Converts to the saga-common leg type the service layer consumes, delegating field validation to it. */
    @Override
    public FlightBookingRequest toBookingRequest() {
        return new FlightBookingRequest(
                this.flightNumber, this.originAirportCode, this.destinationAirportCode,
                this.departureDate, this.passengerCount, this.offerReference);
    }
}
