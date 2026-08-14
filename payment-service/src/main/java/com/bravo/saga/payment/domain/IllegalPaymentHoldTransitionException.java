package com.bravo.saga.payment.domain;

/** Thrown by {@link PaymentHold} when asked to transition to a status its current one disallows. */
public final class IllegalPaymentHoldTransitionException extends RuntimeException {

    public IllegalPaymentHoldTransitionException(final PaymentHoldStatus from,
            final PaymentHoldStatus to) {
        super("Cannot transition payment hold from " + from + " to " + to);
    }
}
