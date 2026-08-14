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
 * steps 4/7, and compensation (use-case-errors.md E2): hold, capture, and release a payment
 * hold. Backs {@code PaymentActivities}, called by the saga Workflow, once
 * {@code payment-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
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

    private Mono<PaymentHold> loadHold(final UUID sagaId) {
        return this.paymentHoldRepository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Payment hold not found: %s".formatted(sagaId))));
    }
}
