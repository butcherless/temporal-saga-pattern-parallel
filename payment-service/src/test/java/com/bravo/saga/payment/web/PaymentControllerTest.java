package com.bravo.saga.payment.web;

import com.bravo.saga.payment.domain.IllegalPaymentHoldTransitionException;
import com.bravo.saga.payment.domain.PaymentHold;
import com.bravo.saga.payment.domain.PaymentHoldStatus;
import com.bravo.saga.payment.service.PaymentHoldProgressionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentHoldProgressionService paymentHoldProgressionService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToController(new PaymentController(this.paymentHoldProgressionService))
                .controllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void holdPaymentReturns201WithTheHold() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold hold = PaymentHold.hold(sagaId, "pat_9f3e2b7c8a1d4e5f", Instant.now());
        when(this.paymentHoldProgressionService.holdPayment(any())).thenReturn(Mono.just(hold));

        this.client.post().uri("/payment-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sagaId":"%s","paymentAuthorizationToken":"pat_9f3e2b7c8a1d4e5f"}""".formatted(sagaId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(PaymentHoldStatus.HELD.name());
    }

    @Test
    void holdPaymentRejectsAnInvalidRequestBodyWithAProblemDetail() {
        final ResponseSpec response = this.client.post().uri("/payment-holds")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sagaId":"%s","paymentAuthorizationToken":null}""".formatted(UUID.randomUUID()))
                .exchange();

        assertProblemDetail(response, 400, "paymentAuthorizationToken must not be null");
    }

    @Test
    void capturePaymentReturns200WithTheCapturedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold captured = PaymentHold.hold(sagaId, "pat_9f3e2b7c8a1d4e5f", Instant.now()).capture(Instant.now());
        when(this.paymentHoldProgressionService.capturePayment(any())).thenReturn(Mono.just(captured));

        this.client.post().uri("/payment-holds/{sagaId}/capture", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(PaymentHoldStatus.CAPTURED.name());
    }

    @Test
    void capturePaymentReturns409WhenTheHoldCannotLegallyBeCaptured() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldProgressionService.capturePayment(any()))
                .thenReturn(Mono.error(new IllegalPaymentHoldTransitionException(PaymentHoldStatus.RELEASED, PaymentHoldStatus.CAPTURED)));

        final ResponseSpec response = this.client.post().uri("/payment-holds/{sagaId}/capture", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition payment hold from RELEASED to CAPTURED");
    }

    @Test
    void releasePaymentHoldReturns200WithTheReleasedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold released = PaymentHold.hold(sagaId, "pat_9f3e2b7c8a1d4e5f", Instant.now()).release(Instant.now());
        when(this.paymentHoldProgressionService.releasePaymentHold(any())).thenReturn(Mono.just(released));

        this.client.post().uri("/payment-holds/{sagaId}/release", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(PaymentHoldStatus.RELEASED.name());
    }

    @Test
    void releasePaymentHoldReturns409WhenTheHoldCannotLegallyBeReleased() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldProgressionService.releasePaymentHold(any()))
                .thenReturn(Mono.error(new IllegalPaymentHoldTransitionException(PaymentHoldStatus.CAPTURED, PaymentHoldStatus.RELEASED)));

        final ResponseSpec response = this.client.post().uri("/payment-holds/{sagaId}/release", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition payment hold from CAPTURED to RELEASED");
    }

    @Test
    void refundPaymentReturns200WithTheRefundedStatus() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold refunded = PaymentHold.hold(sagaId, "pat_9f3e2b7c8a1d4e5f", Instant.now()).capture(Instant.now()).refund(Instant.now());
        when(this.paymentHoldProgressionService.refundPayment(any())).thenReturn(Mono.just(refunded));

        this.client.post().uri("/payment-holds/{sagaId}/refund", sagaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sagaId").isEqualTo(sagaId.toString())
                .jsonPath("$.status").isEqualTo(PaymentHoldStatus.REFUNDED.name());
    }

    @Test
    void refundPaymentReturns409WhenTheHoldIsNotCaptured() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldProgressionService.refundPayment(any()))
                .thenReturn(Mono.error(new IllegalPaymentHoldTransitionException(PaymentHoldStatus.HELD, PaymentHoldStatus.REFUNDED)));

        final ResponseSpec response = this.client.post().uri("/payment-holds/{sagaId}/refund", sagaId).exchange();

        assertProblemDetail(response, 409, "Cannot transition payment hold from HELD to REFUNDED");
    }

    private static void assertProblemDetail(final ResponseSpec response,
            final int expectedStatus,
            final String expectedDetail) {
        response.expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(expectedStatus)
                .jsonPath("$.detail").isEqualTo(expectedDetail);
    }
}
