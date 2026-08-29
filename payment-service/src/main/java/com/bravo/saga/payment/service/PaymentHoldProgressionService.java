package com.bravo.saga.payment.service;

import com.bravo.saga.payment.domain.IllegalPaymentHoldTransitionException;
import com.bravo.saga.payment.domain.PaymentHold;
import com.bravo.saga.payment.domain.PaymentHoldStatus;
import com.bravo.saga.payment.persistence.PaymentHoldRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Payment Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 4/7, and compensation (use-case-errors.md E2/E3/E4): hold, capture, release (while held),
 * and refund (after capture) a payment hold. Backs {@code PaymentActivities}, called by the saga
 * Workflow.
 */
@Slf4j
public class PaymentHoldProgressionService {

    private final PaymentHoldRepository paymentHoldRepository;

    public PaymentHoldProgressionService(final PaymentHoldRepository paymentHoldRepository) {
        this.paymentHoldRepository = Objects.requireNonNull(paymentHoldRepository, "paymentHoldRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing hold unchanged. */
    public Mono<PaymentHold> holdPayment(final HoldPaymentRequest request) {
        log.debug("holdPayment - {}", request);
        return this.paymentHoldRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.paymentHoldRepository.save(
                        PaymentHold.hold(request.sagaId(), request.paymentAuthorizationToken(), request.now()))));
    }

    /**
     * Captures the hold, or returns it unchanged if already {@link PaymentHoldStatus#CAPTURED}.
     *
     * @throws IllegalPaymentHoldTransitionException if the hold cannot legally be captured (e.g. already {@code RELEASED})
     */
    public Mono<PaymentHold> capturePayment(final CapturePaymentRequest request) {
        log.debug("capturePayment - {}", request);
        return this.loadHold(request.sagaId())
                .flatMap(hold -> hold.status() == PaymentHoldStatus.CAPTURED
                        ? Mono.just(hold)
                        : this.paymentHoldRepository.save(hold.capture(request.now())));
    }

    /**
     * Releases the hold (compensation), or returns it unchanged if already {@link PaymentHoldStatus#RELEASED}.
     *
     * @throws IllegalPaymentHoldTransitionException if the hold cannot legally be released (e.g. already {@code CAPTURED} — undoing a capture is a refund, not a release)
     */
    public Mono<PaymentHold> releasePaymentHold(final ReleasePaymentHoldRequest request) {
        log.debug("releasePaymentHold - {}", request);
        return this.loadHold(request.sagaId())
                .flatMap(hold -> hold.status() == PaymentHoldStatus.RELEASED
                        ? Mono.just(hold)
                        : this.paymentHoldRepository.save(hold.release(request.now())));
    }

    /**
     * Refunds a captured hold (post-capture compensation, use-case-errors.md E4), or returns it
     * unchanged if already {@link PaymentHoldStatus#REFUNDED}.
     *
     * @throws IllegalPaymentHoldTransitionException if the hold cannot legally be refunded (only a {@code CAPTURED} hold can be — a still-{@code HELD} one is released instead)
     */
    public Mono<PaymentHold> refundPayment(final RefundPaymentRequest request) {
        log.debug("refundPayment - {}", request);
        return this.loadHold(request.sagaId())
                .flatMap(hold -> hold.status() == PaymentHoldStatus.REFUNDED
                        ? Mono.just(hold)
                        : this.paymentHoldRepository.save(hold.refund(request.now())));
    }

    private Mono<PaymentHold> loadHold(final UUID sagaId) {
        return this.paymentHoldRepository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Payment hold not found: %s".formatted(sagaId))));
    }
}
