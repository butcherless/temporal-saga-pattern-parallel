package com.bravo.saga.booking.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The booking's own functional state machine — docs/travel-booking/business-definitions.md §7's
 * top-level status vocabulary (its {@code Pending} sub-states — Started, Reservations pending,
 * Payment held, Confirmations pending, Compensating — are saga-internal progress, not tracked as
 * separate values of this enum; see docs/travel-booking/use-case-alternatives.md A2, pending, on
 * whether that ever needs exposing). {@code Confirmed}/{@code Cancelled}/{@code Failed} are all
 * terminal: none has an automatic reversal.
 */
public enum TravelBookingStatus {

    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED;

    private static final Map<TravelBookingStatus, Set<TravelBookingStatus>> ALLOWED_TRANSITIONS = buildTransitionGraph();

    /** Whether this state may legally transition directly to {@code target}. */
    public boolean canTransitionTo(final TravelBookingStatus target) {
        Objects.requireNonNull(target, "target must not be null");
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** The set of states this state may legally transition to; empty for terminal states. */
    public Set<TravelBookingStatus> allowedNextStates() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** Whether no further transition is possible from this state. */
    public boolean isTerminal() {
        return this.allowedNextStates().isEmpty();
    }

    private static Map<TravelBookingStatus, Set<TravelBookingStatus>> buildTransitionGraph() {
        final Map<TravelBookingStatus, Set<TravelBookingStatus>> graph = new EnumMap<>(TravelBookingStatus.class);

        graph.put(PENDING, Set.of(CONFIRMED, CANCELLED, FAILED));
        graph.put(CONFIRMED, Set.of());
        graph.put(CANCELLED, Set.of());
        graph.put(FAILED, Set.of());

        return Collections.unmodifiableMap(graph);
    }
}
