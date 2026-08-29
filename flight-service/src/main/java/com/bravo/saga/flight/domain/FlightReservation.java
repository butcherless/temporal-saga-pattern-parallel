package com.bravo.saga.flight.domain;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import com.bravo.saga.leg.domain.IllegalReservationTransitionException;
import com.bravo.saga.leg.domain.ReservationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Flight Service's own aggregate: the reservation behind one Saga leg. Its {@code id} is the
 * Saga's own id — one reservation per saga, same "no separate surrogate id" reasoning as
 * {@code booking-service}'s {@code TravelBooking}. No price field: per
 * docs/travel-booking/business-definitions.md §5, the price is bound to {@code offerReference}
 * at the provider and never carried through this saga as a separate value.
 */
@Table("flight_reservation")
public record FlightReservation(
        @Id UUID id,
        String flightNumber,
        String originAirportCode,
        String destinationAirportCode,
        LocalDate departureDate,
        Integer passengerCount,
        UUID offerReference,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version) {

    public FlightReservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(flightNumber, "flightNumber must not be null");
        Objects.requireNonNull(originAirportCode, "originAirportCode must not be null");
        Objects.requireNonNull(destinationAirportCode, "destinationAirportCode must not be null");
        Objects.requireNonNull(departureDate, "departureDate must not be null");
        Objects.requireNonNull(passengerCount, "passengerCount must not be null");
        Objects.requireNonNull(offerReference, "offerReference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (flightNumber.isBlank()) {
            throw new IllegalArgumentException("flightNumber must not be blank");
        }
        if (passengerCount <= 0) {
            throw new IllegalArgumentException("passengerCount must be positive");
        }
    }

    /** Creates a brand-new reservation for {@code booking}, in {@link ReservationStatus#RESERVED}. */
    public static FlightReservation reserve(final UUID sagaId,
            final FlightBookingRequest booking,
            final Instant now) {
        Objects.requireNonNull(sagaId, "sagaId must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
        return new FlightReservation(sagaId, booking.flightNumber(), booking.originAirportCode(),
                booking.destinationAirportCode(), booking.departureDate(), booking.passengerCount(),
                booking.offerReference(), ReservationStatus.RESERVED, now, now, null);
    }

    /**
     * Confirms the reservation — main use case, step 5.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CONFIRMED}
     */
    public FlightReservation confirm(final Instant now) {
        return this.advanceTo(ReservationStatus.CONFIRMED, now);
    }

    /**
     * Cancels the reservation (compensation) — use-case-errors.md E1/E2.
     *
     * @throws IllegalReservationTransitionException if {@code status} cannot transition to {@link ReservationStatus#CANCELLED}
     */
    public FlightReservation cancel(final Instant now) {
        return this.advanceTo(ReservationStatus.CANCELLED, now);
    }

    private FlightReservation advanceTo(final ReservationStatus target,
            final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalReservationTransitionException(this.status, target);
        }
        return new FlightReservation(this.id, this.flightNumber, this.originAirportCode, this.destinationAirportCode,
                this.departureDate, this.passengerCount, this.offerReference, target, this.createdAt, now, this.version);
    }
}
