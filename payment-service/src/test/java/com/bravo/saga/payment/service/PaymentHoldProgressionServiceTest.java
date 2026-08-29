package com.bravo.saga.payment.service;

import com.bravo.saga.payment.domain.IllegalPaymentHoldTransitionException;
import com.bravo.saga.payment.domain.PaymentHold;
import com.bravo.saga.payment.domain.PaymentHoldStatus;
import com.bravo.saga.payment.persistence.PaymentHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adapter-free unit test for {@link PaymentHoldProgressionService} — {@link PaymentHoldRepository} is mocked. */
@ExtendWith(MockitoExtension.class)
class PaymentHoldProgressionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");
    private static final String TOKEN = "pat_9f3e2b7c8a1d4e5f";

    @Mock
    private PaymentHoldRepository paymentHoldRepository;

    private PaymentHoldProgressionService service;

    @BeforeEach
    void setUp() {
        this.service = new PaymentHoldProgressionService(this.paymentHoldRepository);
        lenient().when(this.paymentHoldRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void holdPaymentCreatesANewHoldWhenNoneExistsForTheSagaId() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.holdPayment(new HoldPaymentRequest(sagaId, TOKEN, NOW)))
                .assertNext(hold -> {
                    assertThat(hold.id()).isEqualTo(sagaId);
                    assertThat(hold.paymentAuthorizationToken()).isEqualTo(TOKEN);
                    assertThat(hold.status()).isEqualTo(PaymentHoldStatus.HELD);
                })
                .verifyComplete();
    }

    @Test
    void holdPaymentIsIdempotentAndReturnsTheExistingHold() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold existing = PaymentHold.hold(sagaId, TOKEN, NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(existing));

        StepVerifier.create(this.service.holdPayment(new HoldPaymentRequest(sagaId, TOKEN, NOW)))
                .assertNext(hold -> assertThat(hold).isEqualTo(existing))
                .verifyComplete();

        verify(this.paymentHoldRepository, never()).save(any());
    }

    @Test
    void capturePaymentAdvancesFromHeldToCaptured() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold held = PaymentHold.hold(sagaId, TOKEN, NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(held));

        StepVerifier.create(this.service.capturePayment(new CapturePaymentRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.CAPTURED))
                .verifyComplete();
    }

    @Test
    void capturePaymentIsIdempotentWhenAlreadyCaptured() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold captured = PaymentHold.hold(sagaId, TOKEN, NOW).capture(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(captured));

        StepVerifier.create(this.service.capturePayment(new CapturePaymentRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.CAPTURED))
                .verifyComplete();

        verify(this.paymentHoldRepository, never()).save(any());
    }

    @Test
    void capturePaymentFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.capturePayment(new CapturePaymentRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void capturePaymentThrowsWhenAlreadyReleased() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold released = PaymentHold.hold(sagaId, TOKEN, NOW).release(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(released));

        StepVerifier.create(this.service.capturePayment(new CapturePaymentRequest(sagaId, NOW)))
                .expectError(IllegalPaymentHoldTransitionException.class)
                .verify();
    }

    @Test
    void releasePaymentHoldAdvancesFromHeldToReleased() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold held = PaymentHold.hold(sagaId, TOKEN, NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(held));

        StepVerifier.create(this.service.releasePaymentHold(new ReleasePaymentHoldRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.RELEASED))
                .verifyComplete();
    }

    @Test
    void releasePaymentHoldIsIdempotentWhenAlreadyReleased() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold released = PaymentHold.hold(sagaId, TOKEN, NOW).release(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(released));

        StepVerifier.create(this.service.releasePaymentHold(new ReleasePaymentHoldRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.RELEASED))
                .verifyComplete();

        verify(this.paymentHoldRepository, never()).save(any());
    }

    @Test
    void releasePaymentHoldFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.releasePaymentHold(new ReleasePaymentHoldRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void releasePaymentHoldThrowsWhenAlreadyCapturedSinceUndoingACaptureIsARefundNotARelease() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold captured = PaymentHold.hold(sagaId, TOKEN, NOW).capture(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(captured));

        StepVerifier.create(this.service.releasePaymentHold(new ReleasePaymentHoldRequest(sagaId, NOW)))
                .expectError(IllegalPaymentHoldTransitionException.class)
                .verify();
    }

    @Test
    void refundPaymentAdvancesFromCapturedToRefunded() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold captured = PaymentHold.hold(sagaId, TOKEN, NOW).capture(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(captured));

        StepVerifier.create(this.service.refundPayment(new RefundPaymentRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.REFUNDED))
                .verifyComplete();
    }

    @Test
    void refundPaymentIsIdempotentWhenAlreadyRefunded() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold refunded = PaymentHold.hold(sagaId, TOKEN, NOW).capture(NOW).refund(NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(refunded));

        StepVerifier.create(this.service.refundPayment(new RefundPaymentRequest(sagaId, NOW)))
                .assertNext(hold -> assertThat(hold.status()).isEqualTo(PaymentHoldStatus.REFUNDED))
                .verifyComplete();

        verify(this.paymentHoldRepository, never()).save(any());
    }

    @Test
    void refundPaymentFailsWhenNotFound() {
        final UUID sagaId = UUID.randomUUID();
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.empty());

        StepVerifier.create(this.service.refundPayment(new RefundPaymentRequest(sagaId, NOW)))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void refundPaymentThrowsWhenTheHoldIsStillHeldSinceAnUncapturedHoldIsReleasedNotRefunded() {
        final UUID sagaId = UUID.randomUUID();
        final PaymentHold held = PaymentHold.hold(sagaId, TOKEN, NOW);
        when(this.paymentHoldRepository.findById(sagaId)).thenReturn(Mono.just(held));

        StepVerifier.create(this.service.refundPayment(new RefundPaymentRequest(sagaId, NOW)))
                .expectError(IllegalPaymentHoldTransitionException.class)
                .verify();
    }
}
