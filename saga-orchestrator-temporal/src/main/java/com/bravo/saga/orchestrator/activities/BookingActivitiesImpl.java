package com.bravo.saga.orchestrator.activities;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.UUID;

/** Calls {@code booking-service} directly over HTTP; no Kafka involved. */
@Slf4j
@Component
public class BookingActivitiesImpl implements BookingActivities {

    private final WebClient bookingWebClient;

    public BookingActivitiesImpl(@Qualifier("bookingWebClient") final WebClient bookingWebClient) {
        this.bookingWebClient = Objects.requireNonNull(bookingWebClient, "bookingWebClient must not be null");
    }

    @Override
    public void confirmBooking(final UUID sagaId) {
        log.debug("confirmBooking - sagaId={}", sagaId);
        this.bookingWebClient.post()
                .uri("/travel-bookings/{sagaId}/confirm", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
