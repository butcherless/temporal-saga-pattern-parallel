package com.bravo.saga.flight;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.flight.service.FlightReservationProgressionService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Shared sample-data fixtures for flight-service's progression-service and controller tests. */
public final class FlightTestFixtures {

    public static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    private FlightTestFixtures() {
    }

    public static FlightBookingRequest sampleBooking() {
        return new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID());
    }

    public static FlightBookingRequest faultInjectingBooking() {
        return new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2,
                FlightReservationProgressionService.SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE);
    }
}
