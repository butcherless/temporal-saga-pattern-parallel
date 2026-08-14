package com.bravo.saga.orchestrator.activities;

import com.bravo.saga.common.workflow.FlightBookingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.UUID;

/** Calls {@code flight-service} directly over HTTP; no Kafka involved. */
@Slf4j
@Component
public class FlightActivitiesImpl implements FlightActivities {

    private final WebClient flightWebClient;

    public FlightActivitiesImpl(@Qualifier("flightWebClient") final WebClient flightWebClient) {
        this.flightWebClient = Objects.requireNonNull(flightWebClient, "flightWebClient must not be null");
    }

    @Override
    public void reserveFlight(final UUID sagaId,
            final FlightBookingRequest flightBooking) {
        log.debug("reserveFlight - sagaId={}, flightBooking={}", sagaId, flightBooking);
        this.flightWebClient.post()
                .uri("/flight-reservations")
                .bodyValue(new FlightReservationRequest(sagaId, flightBooking.flightNumber(), flightBooking.originAirportCode(),
                        flightBooking.destinationAirportCode(), flightBooking.departureDate(), flightBooking.passengerCount(),
                        flightBooking.offerReference()))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void confirmFlight(final UUID sagaId) {
        log.debug("confirmFlight - sagaId={}", sagaId);
        this.flightWebClient.post()
                .uri("/flight-reservations/{sagaId}/confirm", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void cancelFlightReservation(final UUID sagaId) {
        log.debug("cancelFlightReservation - sagaId={}", sagaId);
        this.flightWebClient.post()
                .uri("/flight-reservations/{sagaId}/cancel", sagaId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
