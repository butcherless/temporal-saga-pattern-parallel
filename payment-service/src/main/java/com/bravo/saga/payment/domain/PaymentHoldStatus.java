package com.bravo.saga.payment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A payment hold's own functional state machine — docs/travel-booking/use-case-main.md steps
 * 4/7, and business-definitions.md §3 (Hold/Capture/Refund):
 * <ul>
 *     <li>{@code HELD} advances to {@code CAPTURED} (main use case, step 7) or is {@code RELEASED}
 *     as compensation while still held (use-case-errors.md E2/E3).</li>
 *     <li>{@code CAPTURED} advances to {@code REFUNDED} — post-capture compensation
 *     (use-case-errors.md E4): undoing a capture is a refund, not a hold release.</li>
 *     <li>{@code RELEASED} and {@code REFUNDED} are terminal.</li>
 * </ul>
 */
public enum PaymentHoldStatus {

    HELD,
    CAPTURED,
    RELEASED,
    REFUNDED;

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
        graph.put(CAPTURED, Set.of(REFUNDED));
        graph.put(RELEASED, Set.of());
        graph.put(REFUNDED, Set.of());

        return Collections.unmodifiableMap(graph);
    }
}
