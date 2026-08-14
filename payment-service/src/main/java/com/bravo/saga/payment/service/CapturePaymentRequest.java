package com.bravo.saga.payment.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link PaymentHoldProgressionService#capturePayment(CapturePaymentRequest)}. */
public record CapturePaymentRequest(UUID sagaId,
        Instant now) {

    public CapturePaymentRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
