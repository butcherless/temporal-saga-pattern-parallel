package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.HotelBookingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.UUID;

/** Calls {@code hotel-service} directly over HTTP; no Kafka involved. */
@Slf4j
@Component
public class HotelActivitiesImpl implements HotelActivities {

    private final WebClient hotelWebClient;

    public HotelActivitiesImpl(@Qualifier("hotelWebClient") final WebClient hotelWebClient) {
        this.hotelWebClient = Objects.requireNonNull(hotelWebClient, "hotelWebClient must not be null");
    }

    @Override
    public void reserveHotel(final UUID sagaId,
            final HotelBookingRequest hotelBooking) {
        log.debug("reserveHotel - sagaId={}, hotelBooking={}", sagaId, hotelBooking);
        this.hotelWebClient.post()
                .uri("/hotel-reservations")
                .bodyValue(new HotelReservationRequest(sagaId, hotelBooking.hotelCode(), hotelBooking.roomType(),
                        hotelBooking.checkInDate(), hotelBooking.checkOutDate(), hotelBooking.guestCount(), hotelBooking.offerReference()))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void confirmHotel(final UUID sagaId) {
        log.debug("confirmHotel - sagaId={}", sagaId);
        this.hotelWebClient.post()
                .uri("/hotel-reservations/{sagaId}/confirm", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void cancelHotelReservation(final UUID sagaId) {
        log.debug("cancelHotelReservation - sagaId={}", sagaId);
        this.hotelWebClient.post()
                .uri("/hotel-reservations/{sagaId}/cancel", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
