package com.bravo.saga.orchestrator.activities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class BookingActivitiesImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private BookingActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        this.activities = new BookingActivitiesImpl(ExchangeFunctionStub.webClientFor(this.exchangeFunction));
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);
    }

    @Test
    void confirmBookingPostsToTheConfirmEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();

        this.activities.confirmBooking(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/travel-bookings/%s/confirm".formatted(sagaId));
    }
}
