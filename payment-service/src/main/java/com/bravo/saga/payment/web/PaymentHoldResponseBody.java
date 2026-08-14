package com.bravo.saga.payment.web;

import com.bravo.saga.payment.domain.PaymentHoldStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.UUID;

/** Response body shared by every payment hold endpoint: the hold's own id (the saga's id) and its current status. */
public record PaymentHoldResponseBody(
        @Schema(description = "The saga's own id — also the hold's id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sagaId,
        @Schema(description = "The hold's status", requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentHoldStatus status) {

    public PaymentHoldResponseBody {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
