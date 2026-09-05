package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import java.util.UUID;

import static com.bravo.saga.orchestrator.activities.BookingRequestFixtures.defaultHotelBooking;

@ExtendWith(MockitoExtension.class)
class HotelActivitiesImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private HotelActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        this.activities = new HotelActivitiesImpl(ExchangeFunctionStub.webClientFor(this.exchangeFunction));
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.CREATED);
    }

    @Test
    void reserveHotelPostsToTheReservationsEndpoint() {
        final UUID sagaId = UUID.randomUUID();
        final HotelBookingRequest booking = defaultHotelBooking();

        this.activities.reserveHotel(sagaId, booking);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/hotel-reservations");
    }

    @Test
    void confirmHotelPostsToTheConfirmEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.confirmHotel(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/hotel-reservations/%s/confirm".formatted(sagaId));
    }

    @Test
    void cancelHotelReservationPostsToTheCancelEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.cancelHotelReservation(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/hotel-reservations/%s/cancel".formatted(sagaId));
    }
}
