package com.bravo.saga.payment.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment Service's own aggregate: the hold behind one Saga. Its {@code id} is the Saga's own id
 * — one hold per saga, same "no separate surrogate id" reasoning as {@code booking-service}'s
 * {@code TravelBooking}. No amount field: per docs/travel-booking/business-definitions.md §5 and
 * {@code PaymentActivities}'s own javadoc, the amount to hold/capture is whatever
 * {@code paymentAuthorizationToken} already authorizes — this saga performs no computation of it.
 */
@Table("payment_hold")
public record PaymentHold(
        @Id UUID id,
        String paymentAuthorizationToken,
        PaymentHoldStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    public PaymentHold {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(paymentAuthorizationToken, "paymentAuthorizationToken must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (paymentAuthorizationToken.isBlank()) {
            throw new IllegalArgumentException("paymentAuthorizationToken must not be blank");
        }
    }

    /** Creates a brand-new hold, in {@link PaymentHoldStatus#HELD}. */
    public static PaymentHold hold(final UUID sagaId,
            final String paymentAuthorizationToken,
            final Instant now) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        return new PaymentHold(sagaId, paymentAuthorizationToken, PaymentHoldStatus.HELD, now, now, null);
    }

    /**
     * Captures the hold — main use case, step 7.
     *
     * @throws IllegalPaymentHoldTransitionException if {@code status} cannot transition to {@link PaymentHoldStatus#CAPTURED}
     */
    public PaymentHold capture(final Instant now) {
        return this.advanceTo(PaymentHoldStatus.CAPTURED, now);
    }

    /**
     * Releases the hold (compensation) — use-case-errors.md E2.
     *
     * @throws IllegalPaymentHoldTransitionException if {@code status} cannot transition to {@link PaymentHoldStatus#RELEASED}
     * (e.g. already {@code CAPTURED} — undoing a capture is a refund, not a release, per business-definitions.md §3)
     */
    public PaymentHold release(final Instant now) {
        return this.advanceTo(PaymentHoldStatus.RELEASED, now);
    }

    private PaymentHold advanceTo(final PaymentHoldStatus target,
            final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalPaymentHoldTransitionException(this.status, target);
        }
        return new PaymentHold(this.id, this.paymentAuthorizationToken, target, this.createdAt, now, this.version);
    }
}
