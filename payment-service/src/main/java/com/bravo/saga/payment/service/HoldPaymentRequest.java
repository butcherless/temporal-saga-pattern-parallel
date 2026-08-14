package com.bravo.saga.payment.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input to {@link PaymentHoldProgressionService#holdPayment(HoldPaymentRequest)}. */
public record HoldPaymentRequest(UUID sagaId,
        String paymentAuthorizationToken,
        Instant now) {

    public HoldPaymentRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(paymentAuthorizationToken, "paymentAuthorizationToken must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (paymentAuthorizationToken.isBlank()) {
            throw new IllegalArgumentException("paymentAuthorizationToken must not be blank");
        }
    }
}
