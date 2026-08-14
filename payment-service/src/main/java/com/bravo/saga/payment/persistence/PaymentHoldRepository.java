package com.bravo.saga.payment.persistence;

import com.bravo.saga.payment.domain.PaymentHold;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/** Reactive persistence gateway for {@link PaymentHold} aggregates (table {@code payment_hold}). */
public interface PaymentHoldRepository extends ReactiveCrudRepository<PaymentHold, UUID> {
}
