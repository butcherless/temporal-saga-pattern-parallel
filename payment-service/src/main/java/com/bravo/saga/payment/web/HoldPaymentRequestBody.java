package com.bravo.saga.payment.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Request body for {@code POST /payment-holds}, called by the saga Workflow's PaymentActivities. */
public record HoldPaymentRequestBody(
        @Schema(description = "The saga this hold belongs to; also the hold's own id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(description = "Opaque token authorizing the amount to hold", example = "pat_9f3e2b7c8a1d4e5f", requiredMode = Schema.RequiredMode.REQUIRED)
        String paymentAuthorizationToken) {

    public HoldPaymentRequestBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(paymentAuthorizationToken, "paymentAuthorizationToken must not be null");
    }
}
