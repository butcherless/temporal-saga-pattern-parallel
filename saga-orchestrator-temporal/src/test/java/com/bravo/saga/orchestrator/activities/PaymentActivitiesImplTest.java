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
class PaymentActivitiesImplTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private PaymentActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        this.activities = new PaymentActivitiesImpl(ExchangeFunctionStub.webClientFor(this.exchangeFunction));
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.CREATED);
    }

    @Test
    void holdPaymentPostsToTheHoldsEndpoint() {
        final UUID sagaId = UUID.randomUUID();

        this.activities.holdPayment(sagaId, "pat_9f3e2b7c8a1d4e5f");

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/payment-holds");
    }

    @Test
    void capturePaymentPostsToTheCaptureEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.capturePayment(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/payment-holds/%s/capture".formatted(sagaId));
    }

    @Test
    void releasePaymentHoldPostsToTheReleaseEndpointForTheGivenSagaId() {
        final UUID sagaId = UUID.randomUUID();
        ExchangeFunctionStub.stubResponse(this.exchangeFunction, HttpStatus.OK);

        this.activities.releasePaymentHold(sagaId);

        ExchangeFunctionStub.assertPostedTo(this.exchangeFunction, "/payment-holds/%s/release".formatted(sagaId));
    }
}
