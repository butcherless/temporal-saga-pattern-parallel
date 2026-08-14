package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.CarBookingRequest;
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
class CarActivitiesImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private CarActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        this.activities = new CarActivitiesImpl(ExchangeFunctionStub.webClientFor(this.exchangeFunction));
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.CREATED);
    }

    @Test
    void reserveCarPostsToTheReservationsEndpoint() {
        final UUID sagaId = UUID.randomUUID();
        final CarBookingRequest booking = new CarBookingRequest(
                "COMPACT", "CDG", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), UUID.randomUUID());

        this.activities.reserveCar(sagaId, booking);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/car-reservations");
    }

    @Test
    void confirmCarPostsToTheConfirmEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.confirmCar(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/car-reservations/%s/confirm".formatted(sagaId));
    }

    @Test
    void cancelCarReservationPostsToTheCancelEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.cancelCarReservation(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/car-reservations/%s/cancel".formatted(sagaId));
    }
}
