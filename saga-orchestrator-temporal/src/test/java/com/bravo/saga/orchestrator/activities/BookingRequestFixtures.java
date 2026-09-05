package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.common.workflow.HotelBookingRequest;

import java.time.LocalDate;
import java.util.UUID;

/** Shared sample booking-request fixtures for the five *ActivitiesImplTest classes and the Workflow test. */
public final class BookingRequestFixtures {

    public static final String DEFAULT_PAYMENT_TOKEN = "pat_9f3e2b7c8a1d4e5f";

    private BookingRequestFixtures() {
    }

    public static FlightBookingRequest defaultFlightBooking() {
        return new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID());
    }

    public static HotelBookingRequest defaultHotelBooking() {
        return new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID());
    }

    public static CarBookingRequest defaultCarBooking() {
        return new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID());
    }
}
