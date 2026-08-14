package com.bravo.saga.payment.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link PaymentHoldProgressionService#releasePaymentHold(ReleasePaymentHoldRequest)}. */
public record ReleasePaymentHoldRequest(UUID sagaId,
        Instant now) {

    public ReleasePaymentHoldRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
