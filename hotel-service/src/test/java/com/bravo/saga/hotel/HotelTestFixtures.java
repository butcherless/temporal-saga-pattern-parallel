package com.bravo.saga.hotel;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.hotel.service.HotelReservationProgressionService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Shared sample-data fixtures for hotel-service's progression-service and controller tests. */
public final class HotelTestFixtures {

    public static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    private HotelTestFixtures() {
    }

    public static HotelBookingRequest sampleBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID());
    }

    public static HotelBookingRequest faultInjectingBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2,
                HotelReservationProgressionService.SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE);
    }
}
