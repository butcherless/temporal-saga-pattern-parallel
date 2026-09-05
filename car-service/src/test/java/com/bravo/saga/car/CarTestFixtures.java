package com.bravo.saga.car;

import com.bravo.saga.car.service.CarReservationProgressionService;
import com.bravo.saga.common.workflow.CarBookingRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Shared sample-data fixtures for car-service's progression-service and controller tests. */
public final class CarTestFixtures {

    public static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    private CarTestFixtures() {
    }

    public static CarBookingRequest sampleBooking() {
        return new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID());
    }

    public static CarBookingRequest faultInjectingBooking() {
        return new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14),
                CarReservationProgressionService.SIMULATED_TRANSIENT_FAULT_OFFER_REFERENCE);
    }
}
