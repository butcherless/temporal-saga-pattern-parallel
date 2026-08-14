package com.bravo.saga.booking.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Booking Service's own aggregate: the travel booking behind one Saga. Its {@code id} is the
 * Saga's own id — this project has exactly one booking per saga, same "no separate surrogate id"
 * reasoning as {@code temporal-saga-pattern}'s {@code CustomerOrder}. {@code businessKey} is the
 * idempotency key (docs/travel-booking/business-definitions.md §4), independent of {@code id}.
 */
@Table("travel_booking")
public record TravelBooking(
        @Id UUID id,
        String businessKey,
        String operatorId,
        TravelBookingStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    public TravelBooking {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be blank");
        }
        if (operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId must not be blank");
        }
    }

    /** Creates a brand-new booking, in {@link TravelBookingStatus#PENDING}. */
    public static TravelBooking create(final UUID sagaId,
            final String businessKey,
            final String operatorId,
            final Instant now) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        return new TravelBooking(sagaId, businessKey, operatorId, TravelBookingStatus.PENDING, now, now, null);
    }

    /**
     * Confirms the booking — main use case (docs/travel-booking/use-case-main.md) step 8, called
     * by the saga Workflow's {@code BookingActivities} once payment has been captured.
     *
     * @throws IllegalTravelBookingTransitionException if {@code status} cannot transition to {@link TravelBookingStatus#CONFIRMED}
     */
    public TravelBooking confirm(final Instant now) {
        return advanceTo(TravelBookingStatus.CONFIRMED, now);
    }

    private TravelBooking advanceTo(final TravelBookingStatus target,
            final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!status.canTransitionTo(target)) {
            throw new IllegalTravelBookingTransitionException(status, target);
        }
        return new TravelBooking(id, businessKey, operatorId, target, createdAt, now, version);
    }
}
