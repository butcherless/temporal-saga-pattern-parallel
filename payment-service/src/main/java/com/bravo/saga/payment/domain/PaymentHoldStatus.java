package com.bravo.saga.payment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A payment hold's own functional state machine — docs/travel-booking/use-case-main.md steps
 * 4/7, and business-definitions.md §3 (Hold/Capture): {@code HELD} may either advance to
 * {@code CAPTURED} (main use case, step 7) or be released as compensation
 * (use-case-errors.md E2). {@code CAPTURED} is terminal here: per business-definitions.md §3,
 * undoing a capture is a refund, not a hold release — refund is out of scope (E4).
 */
public enum PaymentHoldStatus {

    HELD,
    CAPTURED,
    RELEASED;

    private static final Map<PaymentHoldStatus, Set<PaymentHoldStatus>> ALLOWED_TRANSITIONS = buildTransitionGraph();

    /** Whether this state may legally transition directly to {@code target}. */
    public boolean canTransitionTo(final PaymentHoldStatus target) {
        Objects.requireNonNull(target, "target must not be null");
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** The set of states this state may legally transition to; empty for terminal states. */
    public Set<PaymentHoldStatus> allowedNextStates() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** Whether no further transition is possible from this state. */
    public boolean isTerminal() {
        return this.allowedNextStates().isEmpty();
    }

    private static Map<PaymentHoldStatus, Set<PaymentHoldStatus>> buildTransitionGraph() {
        final Map<PaymentHoldStatus, Set<PaymentHoldStatus>> graph = new EnumMap<>(PaymentHoldStatus.class);

        graph.put(HELD, Set.of(CAPTURED, RELEASED));
        graph.put(CAPTURED, Set.of());
        graph.put(RELEASED, Set.of());

        return Collections.unmodifiableMap(graph);
    }
}
