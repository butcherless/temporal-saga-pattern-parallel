package com.bravo.saga.orchestrator.activities;

import java.util.UUID;

/** Body sent to {@code payment-service}'s {@code POST /payment-holds}. */
record HoldPaymentRequest(UUID sagaId,
        String paymentAuthorizationToken) {
}
