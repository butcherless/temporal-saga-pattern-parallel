package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import java.time.LocalDate;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class FlightActivitiesImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private FlightActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        this.activities = new FlightActivitiesImpl(ExchangeFunctionStub.webClientFor(this.exchangeFunction));
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.CREATED);
    }

    @Test
    void reserveFlightPostsToTheReservationsEndpoint() {
        final UUID sagaId = UUID.randomUUID();
        final FlightBookingRequest booking = new FlightBookingRequest(
                "IB6275", "MAD", "CDG", LocalDate.of(2026, 9, 10), 2, UUID.randomUUID());

        this.activities.reserveFlight(sagaId, booking);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/flight-reservations");
    }

    @Test
    void confirmFlightPostsToTheConfirmEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.confirmFlight(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/flight-reservations/%s/confirm".formatted(sagaId));
    }

    @Test
    void cancelFlightReservationPostsToTheCancelEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.cancelFlightReservation(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/flight-reservations/%s/cancel".formatted(sagaId));
    }
}
