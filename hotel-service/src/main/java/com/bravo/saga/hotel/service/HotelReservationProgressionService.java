package com.bravo.saga.hotel.service;

import com.bravo.saga.hotel.domain.HotelReservation;
import com.bravo.saga.hotel.domain.IllegalReservationTransitionException;
import com.bravo.saga.hotel.domain.ReservationStatus;
import com.bravo.saga.hotel.persistence.HotelReservationRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Hotel Service's idempotent use cases — main use case (docs/travel-booking/use-case-main.md)
 * steps 2/5, and compensation (use-case-errors.md E1/E2): reserve, confirm, and cancel a hotel
 * reservation. Backs {@code HotelActivities}, called by the saga Workflow, once
 * {@code hotel-service}'s REST layer exists to expose these over HTTP (not yet — see CLAUDE.md).
 */
@Slf4j
public class HotelReservationProgressionService {

    private final HotelReservationRepository hotelReservationRepository;

    public HotelReservationProgressionService(final HotelReservationRepository hotelReservationRepository) {
        this.hotelReservationRepository = Objects.requireNonNull(hotelReservationRepository, "hotelReservationRepository must not be null");
    }

    /** Idempotent by {@code sagaId}: a duplicate call returns the existing reservation unchanged. */
    public Mono<HotelReservation> reserveHotel(final ReserveHotelRequest request) {
        log.debug("reserveHotel - {}", request);
        return this.hotelReservationRepository.findById(request.sagaId())
                .switchIfEmpty(Mono.defer(() -> this.hotelReservationRepository.save(
                        HotelReservation.reserve(request.sagaId(), request.hotelBooking(), request.now()))));
    }

    /**
     * Confirms the reservation, or returns it unchanged if already {@link ReservationStatus#CONFIRMED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be confirmed (e.g. already {@code CANCELLED})
     */
    public Mono<HotelReservation> confirmHotel(final ConfirmHotelRequest request) {
        log.debug("confirmHotel - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CONFIRMED
                        ? Mono.just(reservation)
                        : this.hotelReservationRepository.save(reservation.confirm(request.now())));
    }

    /**
     * Cancels the reservation (compensation), or returns it unchanged if already {@link ReservationStatus#CANCELLED}.
     *
     * @throws IllegalReservationTransitionException if the reservation cannot legally be cancelled (e.g. already {@code CONFIRMED})
     */
    public Mono<HotelReservation> cancelHotelReservation(final CancelHotelReservationRequest request) {
        log.debug("cancelHotelReservation - {}", request);
        return this.loadReservation(request.sagaId())
                .flatMap(reservation -> reservation.status() == ReservationStatus.CANCELLED
                        ? Mono.just(reservation)
                        : this.hotelReservationRepository.save(reservation.cancel(request.now())));
    }

    private Mono<HotelReservation> loadReservation(final UUID sagaId) {
        return this.hotelReservationRepository.findById(sagaId)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("Hotel reservation not found: %s".formatted(sagaId))));
    }
}
