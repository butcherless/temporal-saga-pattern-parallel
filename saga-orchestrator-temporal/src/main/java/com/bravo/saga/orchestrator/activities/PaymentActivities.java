package com.bravo.saga.orchestrator.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Activities calling {@code payment-service}'s REST endpoints directly (no Kafka involved) — not
 * designed yet, see this module's pom.xml description. No amount parameter anywhere: per
 * docs/travel-booking/use-case-main.md step 4, the amount to hold/capture is whatever
 * {@code paymentAuthorizationToken} already authorizes — this saga performs no computation of it.
 */
@ActivityInterface
public interface PaymentActivities {

    @ActivityMethod
    void holdPayment(UUID sagaId,
            String paymentAuthorizationToken);

    @ActivityMethod
    void capturePayment(UUID sagaId);

    /** Compensation while the hold is still HELD (use-case-errors.md E2/E3): releases it. */
    @ActivityMethod
    void releasePaymentHold(UUID sagaId);

    /** Post-capture compensation (use-case-errors.md E4): refunds a hold that was already captured. */
    @ActivityMethod
    void refundPayment(UUID sagaId);
}
