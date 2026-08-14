package com.bravo.saga.car.domain;

import com.bravo.saga.common.workflow.CarBookingRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Car Service's own aggregate: the reservation behind one Saga leg. Its {@code id} is the
 * Saga's own id — one reservation per saga, same "no separate surrogate id" reasoning as
 * {@code booking-service}'s {@code TravelBooking}. No price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference}
 * at the provider and never carried through this saga as a separate value.
 */
@Table("car_reservation")
public record CarReservation(
        @Id UUID id,
        String carCategory,
        String pickupLocation,
        LocalDate pickupDate,
        LocalDate dropoffDate,
        UUID offerReference,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    public CarReservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(carCategory, "carCategory must not be null");
        Objects.requireNonNull(pickupLocation, "pickupLocation must not be null");
        Objects.requireNonNull(pickupDate, "pickupDate must not be null");
        Objects.requireNonNull(dropoffDate, "dropoffDate must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (carCategory.isBlank()) {
            throw new IllegalArgumentException("carCategory must not be blank");
        }
        if (pickupLocation.isBlank()) {
            throw new IllegalArgumentException("pickupLocation must not be blank");
        }
        if (!pickupDate.isBefore(dropoffDate)) {
            throw new IllegalArgumentException("pickupDate must be before dropoffDate");
        }
    }

    /** Creates a brand-new reservation for {@code booking}, in {@link ReservationStatus#RESERVED}. */
    public static CarReservation reserve(final UUID sagaId,
            final CarBookingRequest booking,
            final Instant now) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
        return new CarReservation(sagaId, booking.carCategory(), booking.pickupLocation(),
                booking.pickupDate(), booking.dropoffDate(), booking.offerReference(),
                ReservationStatus.RESERVED, now, now, null);
    }

    /**
     * Confirms the reservation — main use case, step 5.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CONFIRMED}
     */
    public CarReservation confirm(final Instant now) {
        return this.advanceTo(ReservationStatus.CONFIRMED, now);
    }

    /**
     * Cancels the reservation (compensation) — use-case-errors.md E1/E2.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CANCELLED}
     */
    public CarReservation cancel(final Instant now) {
        return this.advanceTo(ReservationStatus.CANCELLED, now);
    }

    private CarReservation advanceTo(final ReservationStatus target,
            final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalReservationTransitionException(this.status, target);
        }
        return new CarReservation(this.id, this.carCategory, this.pickupLocation, this.pickupDate,
                this.dropoffDate, this.offerReference, target, this.createdAt, now, this.version);
    }
}
