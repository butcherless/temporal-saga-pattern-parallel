package com.bravo.saga.booking.web;

import com.bravo.saga.common.workflow.CarBookingRequest;
import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.common.workflow.TravelBookingSagaInput;

import java.time.LocalDate;
import java.util.UUID;

/** Shared sample-data fixtures for booking-service's controller/handler tests: one canonical travel package (flight/hotel/car). */
public final class BookingTestFixtures {

    public static final String OPERATOR_ID = "agency-042";
    public static final String PAYMENT_TOKEN = "pat_9f3e2b7c8a1d4e5f";

    private BookingTestFixtures() {
    }

    public static CreateBookingRequestBody sampleCreateBookingRequestBody(final String operatorId,
            final String businessKey) {
        return new CreateBookingRequestBody(
                operatorId,
                PAYMENT_TOKEN,
                new FlightLegRequestBody("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID()),
                new HotelLegRequestBody("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID()),
                new CarLegRequestBody("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID()),
                businessKey);
    }

    public static String sampleBuyRequestJson(final String businessKey) {
        return """
                {
                  "operatorId": "%s",
                  "paymentAuthorizationToken": "%s",
                  "businessKey": "%s",
                  "flight": {"flightNumber":"IB6275","originAirportCode":"MAD","destinationAirportCode":"CDG","departureDate":"2026-09-10","passengerCount":2,"offerReference":"b6e1a1d2-4b9e-4a3a-9c2e-6f2b8b1a9e10"},
                  "hotel": {"hotelCode":"HTL-PARIS-014","roomType":"DOUBLE_DELUXE","checkInDate":"2026-09-10","checkOutDate":"2026-09-14","guestCount":2,"offerReference":"c7f2b2e3-5c0f-4b4b-8d3f-7a3c9c2b0f21"},
                  "car": {"carCategory":"COMPACT","pickupLocation":"CDG","pickupDate":"2026-09-10","dropoffDate":"2026-09-14","offerReference":"d8a3c3f4-6d1a-4c5c-9e4a-8b4d0d3c1f32"}
                }""".formatted(OPERATOR_ID, PAYMENT_TOKEN, businessKey);
    }

    public static TravelBookingSagaInput sampleSagaInput(final UUID sagaId,
            final String businessKey,
            final String operatorId) {
        return new TravelBookingSagaInput(sagaId, businessKey, operatorId, PAYMENT_TOKEN,
                new FlightBookingRequest("IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID()),
                new HotelBookingRequest("HTL-PARIS-014", "DOUBLE_DELUXE", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), 2, UUID.randomUUID()),
                new CarBookingRequest("COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID()));
    }
}
