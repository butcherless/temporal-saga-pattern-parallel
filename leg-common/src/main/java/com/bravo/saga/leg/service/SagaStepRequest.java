package com.bravo.saga.leg.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Input to a leg's {@code confirm*} and {@code cancel*Reservation} use cases — both need only the
 * saga id and the clock reading. (The {@code reserve*} use case additionally carries the leg's
 * booking details, so it keeps its own per-leg request type.)
 */
public record SagaStepRequest(UUID sagaId,
        Instant now) {

    public SagaStepRequest {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }
}
