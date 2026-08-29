package com.bravo.saga.hotel.domain;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import com.bravo.saga.leg.domain.IllegalReservationTransitionException;
import com.bravo.saga.leg.domain.LegReservation;
import com.bravo.saga.leg.domain.ReservationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Hotel Service's own aggregate: the reservation behind one Saga leg. Its {@code id} is the
 * Saga's own id — one reservation per saga, same "no separate surrogate id" reasoning as
 * {@code booking-service}'s {@code TravelBooking}. No price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference}
 * at the provider and never carried through this saga as a separate value.
 */
@Table("hotel_reservation")
public record HotelReservation(
        @Id UUID id,
        String hotelCode,
        String roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer guestCount,
        UUID offerReference,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) implements LegReservation<HotelReservation> {

    public HotelReservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(hotelCode, "hotelCode must not be null");
        Objects.requireNonNull(roomType, "roomType must not be null");
        Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        Objects.requireNonNull(checkOutDate, "checkOutDate must not be null");
        Objects.requireNonNull(guestCount, "guestCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (hotelCode.isBlank()) {
            throw new IllegalArgumentException("hotelCode must not be blank");
        }
        if (guestCount <= 0) {
            throw new IllegalArgumentException("guestCount must be positive");
        }
        if (!checkInDate.isBefore(checkOutDate)) {
            throw new IllegalArgumentException("checkInDate must be before checkOutDate");
        }
    }

    /** Creates a brand-new reservation for {@code booking}, in {@link ReservationStatus#RESERVED}. */
    public static HotelReservation reserve(final UUID sagaId,
            final HotelBookingRequest booking,
            final Instant now) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
        return new HotelReservation(sagaId, booking.hotelCode(), booking.roomType(),
                booking.checkInDate(), booking.checkOutDate(), booking.guestCount(),
                booking.offerReference(), ReservationStatus.RESERVED, now, now, null);
    }

    /**
     * Confirms the reservation — main use case, step 5.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CONFIRMED}
     */
    public HotelReservation confirm(final Instant now) {
        return this.advanceTo(ReservationStatus.CONFIRMED, now);
    }

    /**
     * Cancels the reservation (compensation) — use-case-errors.md E1/E2.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CANCELLED}
     */
    public HotelReservation cancel(final Instant now) {
        return this.advanceTo(ReservationStatus.CANCELLED, now);
    }

    private HotelReservation advanceTo(final ReservationStatus target,
            final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalReservationTransitionException(this.status, target);
        }
        return new HotelReservation(this.id, this.hotelCode, this.roomType, this.checkInDate,
                this.checkOutDate, this.guestCount, this.offerReference, target, this.createdAt, now, this.version);
    }
}
