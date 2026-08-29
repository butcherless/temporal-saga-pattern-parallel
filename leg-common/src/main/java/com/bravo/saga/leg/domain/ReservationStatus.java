package com.bravo.saga.leg.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A reservation leg's own functional state machine — docs/travel-booking/use-case-main.md
 * steps 2/5, and business-definitions.md §3 (Reservation/Confirmation). {@code CONFIRMED} is
 * terminal: once payment is captured and the leg confirmed, there is no automatic reversal.
 *
 * <p>Shared verbatim by flight-service, hotel-service, and car-service — the three legs have
 * the same lifecycle.
 */
public enum ReservationStatus {

    RESERVED,
    CONFIRMED,
    CANCELLED;

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = buildTransitionGraph();

    /** Whether this state may legally transition directly to {@code target}. */
    public boolean canTransitionTo(final ReservationStatus target) {
        Objects.requireNonNull(target, "target must not be null");
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** The set of states this state may legally transition to; empty for terminal states. */
    public Set<ReservationStatus> allowedNextStates() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** Whether no further transition is possible from this state. */
    public boolean isTerminal() {
        return this.allowedNextStates().isEmpty();
    }

    private static Map<ReservationStatus, Set<ReservationStatus>> buildTransitionGraph() {
        final Map<ReservationStatus, Set<ReservationStatus>> graph = new EnumMap<>(ReservationStatus.class);

        graph.put(RESERVED, Set.of(CONFIRMED, CANCELLED));
        graph.put(CONFIRMED, Set.of());
        graph.put(CANCELLED, Set.of());

        return Collections.unmodifiableMap(graph);
    }
}
